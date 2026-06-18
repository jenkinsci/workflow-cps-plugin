# CFS (Completely Fair Scheduler) for CPS Thread Execution

## Overview

The CPS VM executes Groovy pipeline code transformed into continuation-passing style. Before CFS, the scheduler in `CpsThreadGroup.run()` executed **all** runnable threads in insertion (ID) order to exhaustion in a single pass. A rogue Groovy loop that rarely suspends could monopolize the CPS VM thread, delaying all other pipeline branches. A standing `// TODO` at line 499 acknowledged this.

The CFS scheduler replaces that loop with a fairness algorithm adapted from the Linux kernel's Completely Fair Scheduler: each thread accumulates virtual runtime proportional to its CPU consumption, and the lowest-`vruntime` thread executes next.

## Key Concepts

### Virtual Runtime (vruntime)

Each `CpsThread` has a `long vruntime` field (nanoseconds). When a thread executes a chunk of CPS code, its vruntime is incremented:

```
vruntime += (elapsedWallClockNs * 1024) / max(1, weight)
```

The division by `weight` means higher-weight threads accumulate vruntime more slowly and thus get more CPU time. The `max(1, weight)` guard handles deserialized threads whose `weight` field defaulted to 0.

### Scheduling Weight

Each thread has an `int weight` field, defaulting to 1024 (matching Linux CFS `NICE_0_LOAD`). Higher weight → more CPU share. Threads that frequently wait for external events (agent responses, semaphores) could be given higher weights in future enhancements.

### Fair Queue Selection

Each pass of `CpsThreadGroup.run()` scans all runnable threads and picks the one with the **lowest vruntime**:

```
for each runnable thread:
    if thread.vruntime < chosen.vruntime:
        chosen = thread
```

Only that thread executes `runNextChunk()`. If any threads remain runnable after execution, the scheduler re-submits itself to process them in subsequent passes (via the existing `scheduleRun()` → `stillRunnable` loop).

### New Thread Initialization

When a new thread is first `resume()`d (vruntime == 0), it inherits the group's current `min_vruntime`:

```java
if (vruntime == 0) {
    vruntime = group.getMinVruntime();
}
```

This prevents new threads from being starved: they start at the minimum already-accumulated vruntime among existing threads, giving them prompt attention. Threads deserialized from a previous execution keep their existing vruntime.

## Dynamic Quantum

The `computeQuantumNs()` method computes a per-pass execution quantum that scales with system load:

```
quantum_ns = baseQuantumNs / max(1, activeBuildCount / loadFactor)
```

Where:
- **baseQuantumNs** = system property `org.jenkinsci.plugins.workflow.cps.CFS.baseQuantumMs` × 1,000,000 (default: 50,000,000 ns = 50ms)
- **activeBuildCount** = number of `FlowExecution` instances where `!fe.isComplete()`
- **loadFactor** = system property `org.jenkinsci.plugins.workflow.cps.CFS.loadFactor` (default: 10)
- **Floor** = `MIN_QUANTUM_NS` = 5,000,000 ns (5ms), hardcoded to prevent thrashing

### Cooperative, Not Preemptive

The quantum is **cooperative** — it is checked *after* `runNextChunk()` returns, not during execution. There is no mechanism to preempt a running CPS chunk mid-loop (the only hard limit is the existing 5-minute `Timeout`). This is by design: preempting a CPS continuation mid-evaluation would require saving/restoring a partial `Next` chain, which is complex and error-prone.

In practice, nearly all CPS chunks suspend naturally at a step boundary (`sleep`, `sh`, `semaphore`) in microseconds. The quantum only affects threads that repeatedly yield-and-become-runnable-again without suspending — a rare pattern that occurs when a safepoint `ThreadTask` fires in a tight Groovy loop. In that case, the quantum bounds how many consecutive passes that thread can dominate before the scheduler picks a different thread.

This means a 50ms default generates **zero additional overhead** — the CFS scheduler adds one O(n) scan of runnable threads per pass (where n is typically 1–5), and the quantum path is only taken in the uncommon yield-without-suspend case. The real fairness guarantee comes from vruntime ordering: CPU-heavy threads accumulate higher vruntime and naturally lose priority on subsequent passes.

### Quantum Scaling Examples

| Active Builds | Quantum (default loadFactor=10) |
|---------------|-------------------------------|
| 1 | 50ms / max(1, 1/10) = 50ms |
| 10 | 50ms / max(1, 10/10) = 50ms |
| 50 | 50ms / max(1, 50/10) = 50ms / 5 = 10ms |
| 200 | 50ms / max(1, 200/10) = 50ms / 20 = 2.5ms → clamped to 5ms floor |

Setting `baseQuantumMs=0` via system property effectively disables quantum enforcement (returns `Long.MAX_VALUE`).

## Configuration

All configuration is via JVM system properties, set in Jenkins startup arguments or via the Script Console.

### `org.jenkinsci.plugins.workflow.cps.CFS.baseQuantumMs`
- **Type:** `long` (milliseconds)
- **Default:** 50
- **Effect:** Maximum wall-clock time a single thread can execute per scheduler pass. Higher values allow more work per pass but increase the risk of a single busy thread delaying others. Lower values improve fairness at the cost of more scheduler passes.
- **Setting to 0:** Disables quantum enforcement entirely (returns `Long.MAX_VALUE`), reverting to "run thread until it suspends" behavior (while keeping CFS fair ordering).

Example: `-Dorg.jenkinsci.plugins.workflow.cps.CFS.baseQuantumMs=25` to halve the base quantum.

### `org.jenkinsci.plugins.workflow.cps.CFS.loadFactor`
- **Type:** `int`
- **Default:** 10
- **Effect:** Controls how aggressively the quantum shrinks under load. A lower value means the quantum starts shrinking at fewer active builds. A higher value keeps the quantum larger for longer.
- **Setting to 1:** Quantum shrinks immediately: `50ms / max(1, activeBuilds/1)`, meaning 50 builds → 50ms / 50 = 1ms → clamped to 5ms.

Example: `-Dorg.jenkinsci.plugins.workflow.cps.CFS.loadFactor=20` to keep quantum larger under load.

## Performance Considerations

### Scheduler Overhead

Each pass adds one O(n) scan across runnable threads (typically 1–5 threads per pipeline, rarely more than 20 across all parallel branches). This is negligible compared to the Groovy CPS evaluation and sandbox checks inside each chunk. The pass setup (thread-local manipulation, timing capture) and teardown (logging at FINE) are constant-time operations measured in nanoseconds.

### Serialization Impact

The new `vruntime` (long) and `weight` (int) fields on `CpsThread` are Java primitives, handled automatically by the existing River serialization. Old `program.dat` files deserialize with `vruntime=0` and `weight=0`; the `resume()` method corrects `weight=0` to `1024` on first use. No migration is needed.

## How It Compares to the Original Scheduler

| Aspect | Original (before CFS) | CFS |
|--------|----------------------|-----|
| Selection | All runnable threads, ID order | One thread, lowest vruntime |
| Fairness | Later threads (higher IDs) always run last | Equal share among all threads |
| Preemption | None (runs until suspension) | None (cooperative; quantum checked post-chunk) |
| New threads | Starved until existing threads complete | Start at min_vruntime |
| Service latency | Unbounded (rogue loops starve everyone) | Bounded by vruntime; CPU-heavy threads deprioritized |
| Scheduler wait | Measured but not acted on | Measured and used for ordering |
| Configuration | None | Two system properties |
| Per-pass overhead | O(n) scan + execute all runnable | O(n) scan + execute one thread |

## Observability

Each scheduler pass logs at `FINE` level to the logger `org.jenkinsci.plugins.workflow.cps.CpsThreadGroup`:

```
scheduler pass #42: 3 threads total, 2 runnable, pick=1, 15ms, reentrant=0
```

Where:
- **pass #N**: sequential pass counter
- **threads total**: all threads in the group (alive + runnable + suspended)
- **runnable**: threads waiting to execute
- **pick**: the thread ID selected by CFS
- **Nms**: wall-clock duration of this pass
- **reentrant**: number of times `scheduleRun()` was called while already busy

The `schedulerWait` timing in the thread dump measures how long each thread waited runnable before being picked.

## Future Enhancements

Potential directions for extending the scheduler:

1. **Per-thread weight boosting**: Threads waiting for agent responses could get a weight boost (e.g., ×2) so their vruntime accumulates slower and they get CPU sooner, reducing agent idle time.

2. **Thread affinity for same FlowHead**: Threads sharing a `FlowHead` could be scheduled consecutively to improve caller-callee locality.

3. **Pipeline-level fairness**: vruntime could be tracked per `CpsFlowExecution` (not just per `CpsThread`), ensuring fair CPU distribution across multiple pipeline builds sharing the same master thread pool.

4. **CFS time slice**: Instead of one thread per pass, execute up to N threads per pass, each limited by the quantum, reducing scheduler overhead when many threads are runnable.
