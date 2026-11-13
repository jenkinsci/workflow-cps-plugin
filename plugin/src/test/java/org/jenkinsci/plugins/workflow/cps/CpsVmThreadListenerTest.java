package org.jenkinsci.plugins.workflow.cps;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.ExtensionList;
import hudson.model.Result;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

public class CpsVmThreadListenerTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule();

    /**
     * Every task and every program run is closed on its thread, the last opened first. A program run is told of the
     * new heads of its own thread only, and the asynchronous graph listeners that the CPS VM notifies run in a task.
     * (The start node of the flow is added before the CPS VM runs, so its listeners run on the thread that starts the
     * build.)
     */
    @Test
    public void tasksAndProgramRuns() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                echo 'start'
                withEnv(['X=1']) {
                  echo 'in block'
                }
                parallel one: { echo 'one' }, two: { echo 'two' }
                semaphore 'wait'
                echo 'end'
                """, true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait/1", b);
        SemaphoreStep.success("wait/1", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));

        Recorder recorder = ExtensionList.lookupSingleton(Recorder.class);
        assertThat(recorder.problems, empty());
        assertThat(recorder.tasks.get(), greaterThan(0));
        List<List<String>> runs = List.copyOf(recorder.runs);
        // Each run sees the nodes of its own thread only. A block step's body runs in the next run of the same thread,
        // from the body's start node; a branch of parallel in a run of the branch's thread.
        List<String> echoes = List.of("echo start", "echo in block", "echo one", "echo two", "echo end");
        for (String echo : echoes) {
            List<String> run = runWith(runs, echo);
            for (String other : echoes) {
                if (!other.equals(echo) && !(echo.equals("echo start") && other.equals("echo in block"))) {
                    assertThat(echo + " " + run, run, not(hasItem(other)));
                }
            }
        }
        assertThat(runWith(runs, "echo start"), hasItem("withEnv X"));
        assertThat(runWith(runs, "echo in block").get(0), is("at {"));
        assertThat(runWith(runs, "echo end").get(0), is("at semaphore wait"));

        AsyncListener async = ExtensionList.lookupSingleton(AsyncListener.class);
        assertThat(async.inCpsVm.get(), greaterThan(0));
        assertThat(async.outsideTask, empty());
    }

    /** A listener that throws is logged; the build goes on. */
    @Test
    public void failingListener() throws Exception {
        logging.record(CpsVmThreadListener.class, Level.WARNING).capture(100);
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo 'hello'", true));
        r.assertLogContains("hello", r.buildAndAssertSuccess(p));
        List<String> messages = logging.getMessages();
        assertThat(messages, hasItem(containsString("failed around a CPS VM task of")));
        assertThat(messages, hasItem(containsString("failed on the new head")));
        assertThat(messages, hasItem(containsString("failed to close")));
    }

    /**
     * Each node that the asynchronous graph listeners are notified of gets one open and one close of
     * {@link CpsVmThreadListener#aroundAsyncNotification}, around those listeners and on their thread, closed before
     * the next node opens. The synchronous listeners run outside it. The start nodes of two branches of
     * {@code parallel}, notified in one task, get a pair each. The start node of the flow is notified outside the CPS
     * VM, on the thread that starts the build, and gets its pair there.
     */
    @Test
    public void asyncNotifications() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                echo 'start'
                withEnv(['X=1']) {
                  echo 'in block'
                }
                parallel one: { echo 'one' }, two: { echo 'two' }
                semaphore 'wait'
                echo 'end'
                """, true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait/1", b);
        SemaphoreStep.success("wait/1", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));

        Notifications notifications = ExtensionList.lookupSingleton(AsyncNotificationRecorder.class);
        Map<String, String> opened = notifications.assertOnePairPerNotifiedNode(b);
        List<FlowNode> nodes = new DepthFirstScanner().allNodes(b.getExecution());
        FlowNode start = nodes.stream()
                .filter(FlowStartNode.class::isInstance)
                .findFirst()
                .orElseThrow();
        assertThat(opened.get(start.getId()), startsWith("thread "));
        List<String> branches = nodes.stream()
                .filter(node -> node.getPersistentAction(ThreadNameAction.class) != null)
                .map(FlowNode::getId)
                .toList();
        assertThat(branches, hasSize(2));
        assertThat(opened.get(branches.get(0)), startsWith("task "));
        assertThat(
                "both branch start nodes in one task: " + opened,
                opened.get(branches.get(1)),
                equalTo(opened.get(branches.get(0))));
        assertThat(notifications.synchronousNodes, not(empty()));
    }

    /** After an asynchronous graph listener that throws, the notification of its node is still closed once. */
    @Test
    public void asyncNotificationClosedWhenGraphListenerThrows() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo 'a'; parallel one: { echo 'one' }, two: { echo 'two' }", true));
        r.buildAndAssertSuccess(p);
        ExtensionList.lookupSingleton(ThrowingGraphListenerRecorder.class)
                .assertOnePairPerNotifiedNode(p.getLastBuild());
        assertThat(
                ExtensionList.lookupSingleton(ThrowingGraphListener.class).calls.get(), greaterThan(0));
    }

    /** A hook that throws is logged and skipped: the other hooks and the graph listeners still run. */
    @Test
    public void failingAsyncNotificationHook() throws Exception {
        logging.record(CpsVmThreadListener.class, Level.WARNING).capture(100);
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo 'hello'", true));
        r.assertLogContains("hello", r.buildAndAssertSuccess(p));
        assertThat(logging.getMessages(), hasItem(containsString("failed around the notification of")));
        ExtensionList.lookupSingleton(FailingHookRecorder.class).assertOnePairPerNotifiedNode(p.getLastBuild());
    }

    /**
     * When the execution ends after a fatal error outside the CPS VM, its end node is notified on a {@code Timer}
     * thread, and gets its pair there.
     */
    @Test
    public void asyncNotificationOnTimer() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("semaphore 'wait'", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("wait/1", b);
        ((CpsFlowExecution) b.getExecution()).croak(new AbortException("fatal error outside the CPS VM"));
        r.assertBuildStatus(Result.FAILURE, r.waitForCompletion(b));
        Map<String, String> opened =
                ExtensionList.lookupSingleton(TimerRecorder.class).assertOnePairPerNotifiedNode(b);
        FlowNode end = new DepthFirstScanner()
                .allNodes(b.getExecution()).stream()
                        .filter(FlowEndNode.class::isInstance)
                        .findFirst()
                        .orElseThrow();
        assertThat(opened.get(end.getId()), startsWith("thread jenkins.util.Timer"));
    }

    private static List<String> runWith(List<List<String>> runs, String head) {
        List<List<String>> with =
                runs.stream().filter(run -> run.contains(head)).toList();
        assertThat(head + " in " + runs, with, hasSize(1));
        return with.get(0);
    }

    private static String describe(FlowNode node) {
        if (node == null) {
            return "no node";
        }
        String arguments = ArgumentsAction.getStepArgumentsAsString(node);
        return (node.getDisplayFunctionName() + " " + Objects.toString(arguments, "")).trim();
    }

    @TestExtension("tasksAndProgramRuns")
    public static class Recorder implements CpsVmThreadListener {
        static final ThreadLocal<Deque<Object>> OPEN = ThreadLocal.withInitial(ArrayDeque::new);
        final List<String> problems = new CopyOnWriteArrayList<>();
        final List<List<String>> runs = new CopyOnWriteArrayList<>();
        final AtomicInteger tasks = new AtomicInteger();

        @Override
        public AutoCloseable aroundTask(@NonNull CpsFlowExecution execution) {
            Thread thread = Thread.currentThread();
            Object task = new Object();
            OPEN.get().push(task);
            tasks.incrementAndGet();
            return () -> close(task, thread, "task");
        }

        @Override
        public ProgramRun aroundProgramRun(@NonNull CpsFlowExecution execution, FlowNode head) {
            Thread thread = Thread.currentThread();
            if (OPEN.get().isEmpty()) {
                problems.add("program run outside a task, at " + describe(head));
            }
            List<String> heads = new CopyOnWriteArrayList<>();
            heads.add("at " + describe(head));
            runs.add(heads);
            ProgramRun run = new ProgramRun() {
                @Override
                public void onNewHead(@NonNull FlowNode newHead) {
                    if (Thread.currentThread() != thread) {
                        problems.add("new head " + describe(newHead) + " on another thread");
                    }
                    if (OPEN.get().peek() != this) {
                        problems.add("new head " + describe(newHead) + " outside its program run");
                    }
                    heads.add(describe(newHead));
                }

                @Override
                public void close() {
                    Recorder.this.close(this, thread, "program run " + heads);
                }
            };
            OPEN.get().push(run);
            return run;
        }

        private void close(Object opened, Thread thread, String what) {
            if (Thread.currentThread() != thread) {
                problems.add(what + " closed on another thread");
            }
            Deque<Object> open = OPEN.get();
            if (open.peek() == opened) {
                open.pop();
            } else {
                problems.add(what + " closed out of order");
            }
        }
    }

    @TestExtension("tasksAndProgramRuns")
    public static class AsyncListener implements GraphListener {
        final AtomicInteger inCpsVm = new AtomicInteger();
        final List<String> outsideTask = new CopyOnWriteArrayList<>();

        @Override
        public void onNewHead(FlowNode node) {
            if (CpsThreadGroup.current() == null) {
                return; // not notified by the CPS VM
            }
            inCpsVm.incrementAndGet();
            if (Recorder.OPEN.get().isEmpty()) {
                outsideTask.add(describe(node));
            }
        }
    }

    @TestExtension("failingListener")
    public static class Failing implements CpsVmThreadListener {
        @Override
        public AutoCloseable aroundTask(@NonNull CpsFlowExecution execution) {
            throw new IllegalStateException("aroundTask");
        }

        @Override
        public ProgramRun aroundProgramRun(@NonNull CpsFlowExecution execution, FlowNode head) {
            return new ProgramRun() {
                @Override
                public void onNewHead(@NonNull FlowNode newHead) {
                    throw new IllegalStateException("onNewHead");
                }

                @Override
                public void close() {
                    throw new IllegalStateException("close");
                }
            };
        }
    }

    /**
     * Records {@link CpsVmThreadListener#aroundAsyncNotification} and what the graph listeners see. One subclass per
     * test, so that each test has its own.
     */
    abstract static class Notifications implements CpsVmThreadListener {
        /** The node whose asynchronous notification is open on this thread. */
        static final ThreadLocal<FlowNode> NOTIFYING = new ThreadLocal<>();

        static final ThreadLocal<Integer> TASK = new ThreadLocal<>();
        final AtomicInteger tasks = new AtomicInteger();
        final List<String> problems = new CopyOnWriteArrayList<>();
        /** Node id to where its notification was opened: {@code task <n>} on a CPS VM task, else the thread. */
        final Map<String, List<String>> opened = new ConcurrentHashMap<>();

        final Map<String, AtomicInteger> closed = new ConcurrentHashMap<>();
        final Map<String, AtomicInteger> notified = new ConcurrentHashMap<>();
        final Set<String> synchronousNodes = ConcurrentHashMap.newKeySet();

        @Override
        public AutoCloseable aroundTask(@NonNull CpsFlowExecution execution) {
            Integer outer = TASK.get();
            TASK.set(tasks.incrementAndGet());
            return () -> TASK.set(outer);
        }

        @Override
        public AutoCloseable aroundAsyncNotification(@NonNull CpsFlowExecution execution, @NonNull FlowNode node) {
            Thread thread = Thread.currentThread();
            FlowNode open = NOTIFYING.get();
            if (open != null) {
                problems.add("opened " + node.getId() + " while " + open.getId() + " is open");
            }
            NOTIFYING.set(node);
            Integer task = TASK.get();
            opened.computeIfAbsent(node.getId(), id -> new CopyOnWriteArrayList<>())
                    .add(task != null ? "task " + task : "thread " + thread.getName());
            return () -> {
                if (Thread.currentThread() != thread) {
                    problems.add("closed " + node.getId() + " on another thread");
                }
                if (NOTIFYING.get() != node) {
                    problems.add("closed " + node.getId() + " but " + NOTIFYING.get() + " is open");
                }
                NOTIFYING.remove();
                closed.computeIfAbsent(node.getId(), id -> new AtomicInteger()).incrementAndGet();
            };
        }

        void onAsync(FlowNode node) {
            if (NOTIFYING.get() != node) {
                problems.add("asynchronous listener told of " + node.getId() + " outside its notification");
            }
            notified.computeIfAbsent(node.getId(), id -> new AtomicInteger()).incrementAndGet();
        }

        void onSynchronous(FlowNode node) {
            if (NOTIFYING.get() != null) {
                problems.add("synchronous listener told of " + node.getId() + " inside a notification");
            }
            synchronousNodes.add(node.getId());
        }

        /**
         * Asserts that every node of the build was notified once, inside one open and one close.
         *
         * @return node id to where its notification was opened
         */
        Map<String, String> assertOnePairPerNotifiedNode(WorkflowRun b) throws InterruptedException {
            List<String> nodes = new DepthFirstScanner()
                    .allNodes(b.getExecution()).stream().map(FlowNode::getId).toList();
            // the notification of the end node completes the build: its close can run after the build has completed
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (nodes.stream().anyMatch(id -> count(closed, id) == 0) && System.nanoTime() < deadline) {
                Thread.sleep(10);
            }
            assertThat(problems, empty());
            Map<String, String> where = new TreeMap<>();
            for (String id : nodes) {
                assertThat("notifications of " + id, count(notified, id), is(1));
                assertThat("opens of " + id + " " + opened, opened.getOrDefault(id, List.of()), hasSize(1));
                assertThat("closes of " + id, count(closed, id), is(1));
                where.put(id, opened.get(id).get(0));
            }
            assertThat(opened.keySet(), equalTo(Set.copyOf(nodes)));
            return where;
        }

        private static int count(Map<String, AtomicInteger> counts, String id) {
            AtomicInteger count = counts.get(id);
            return count == null ? 0 : count.get();
        }
    }

    @TestExtension("asyncNotifications")
    public static class AsyncNotificationRecorder extends Notifications {}

    @TestExtension("asyncNotificationClosedWhenGraphListenerThrows")
    public static class ThrowingGraphListenerRecorder extends Notifications {}

    @TestExtension("failingAsyncNotificationHook")
    public static class FailingHookRecorder extends Notifications {}

    @TestExtension("asyncNotificationOnTimer")
    public static class TimerRecorder extends Notifications {}

    @TestExtension({
        "asyncNotifications",
        "asyncNotificationClosedWhenGraphListenerThrows",
        "failingAsyncNotificationHook",
        "asyncNotificationOnTimer"
    })
    public static class RecordingAsyncListener implements GraphListener {
        @Override
        public void onNewHead(FlowNode node) {
            ExtensionList.lookup(Notifications.class).forEach(n -> n.onAsync(node));
        }
    }

    @TestExtension("asyncNotifications")
    public static class RecordingSynchronousListener implements GraphListener.Synchronous {
        @Override
        public void onNewHead(FlowNode node) {
            ExtensionList.lookup(Notifications.class).forEach(n -> n.onSynchronous(node));
        }
    }

    @TestExtension("asyncNotificationClosedWhenGraphListenerThrows")
    public static class ThrowingGraphListener implements GraphListener {
        final AtomicInteger calls = new AtomicInteger();

        @Override
        public void onNewHead(FlowNode node) {
            calls.incrementAndGet();
            throw new IllegalStateException("graph listener of " + node.getId());
        }
    }

    @TestExtension("failingAsyncNotificationHook")
    public static class FailingHook implements CpsVmThreadListener {
        @Override
        public AutoCloseable aroundAsyncNotification(@NonNull CpsFlowExecution execution, @NonNull FlowNode node) {
            throw new IllegalStateException("aroundAsyncNotification");
        }
    }
}
