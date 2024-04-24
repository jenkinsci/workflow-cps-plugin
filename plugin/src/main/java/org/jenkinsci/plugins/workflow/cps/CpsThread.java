/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;
import com.google.common.util.concurrent.FutureCallback;
import java.io.IOException;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


import static java.util.logging.Level.FINE;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.PROGRAM;

import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.support.concurrent.Futures;
import org.jenkinsci.plugins.workflow.support.concurrent.Timeout;

/**
 * Represents a {@link Continuable} that is either runnable or suspended (that waits for an
 * external event.)
 *
 * @author Kohsuke Kawaguchi
 */
@PersistIn(PROGRAM)
public final class CpsThread implements Serializable {
    /**
     * Owner object. A thread always belong to a {@link CpsThreadGroup}
     */
    @NonNull
    final CpsThreadGroup group;

    /**
     * Unique ID of this thread among all the threads in the past or future under the same {@link CpsThreadGroup}.
     * This acts as a persistable handle for {@link CpsStepContext} to
     * {@linkplain CpsStepContext#getThread(CpsThreadGroup) refer back to the thread},
     * because they are persisted separately.
     */
    public final int id;

    /**
     * Represents the remaining computation.
     */
    private volatile Continuable program;

    /**
     * The value that feeds into the next execution of {@link #program}. Even though this is an input
     * from this class' point of view, it's typed as {@link Outcome} because from the CPS-transformed
     * program's point of view, this value acts as a return value (or an exception thrown)
     * from {@link Continuable#suspend(Object)}
     */
    Outcome resumeValue;

    /**
     * Promise that {@link Continuable#run0(Outcome)} gets eventually invoked with {@link #resumeValue}.
     */
    private transient CompletableFuture<Object> promise;

    /**
     * The head of the flow node graph that this thread is growing.
     *
     * <p>
     * We create {@link CpsThread}s liberally in {@link CpsBodyExecution#launch(CpsBodyInvoker, CpsThread, FlowHead)},
     * and so multiple {@link CpsThread}s often share the same flow head.
     */
    final FlowHead head;

    @Nullable
    private ContextVariableSet contextVariables;

    /**
     * If this thread is waiting for a {@link StepExecution} to complete (by invoking our callback),
     * this field is set to that execution.
     */
    private StepExecution step;

    /**
     * Gets called when the thread is done.
     */
    private final List<FutureCallback<Object>> completionHandlers = new ArrayList<>();

    CpsThread(CpsThreadGroup group, int id, @NonNull Continuable program, FlowHead head, ContextVariableSet contextVariables) {
        this.group = group;
        this.id = id;
        this.program = group.getExecution().isSandbox() ? new SandboxContinuable(program,this) : program;
        this.head = head;
        this.contextVariables = contextVariables;
    }

    public CpsThreadGroup getGroup() {
        return group;
    }

    public CpsFlowExecution getExecution() {
        return group.getExecution();
    }

    <T> T getContextVariable(Class<T> key, ContextVariableSet.ThrowingSupplier<FlowExecution> execution, ContextVariableSet.ThrowingSupplier<FlowNode> node) throws IOException, InterruptedException {
        LOGGER.fine(() -> "looking up " + key.getName() + " from " + contextVariables);
        T v = contextVariables != null ? contextVariables.get(key, execution, node) : null;
        if (v != null) {
            return v;
        } else if (key == CpsThread.class) {
            return key.cast(this);
        } else if (key == CpsThreadGroup.class) {
            return key.cast(group);
        } else {
            return null;
        }
    }

    public ContextVariableSet getContextVariables() {
        return contextVariables;
    }

    boolean isRunnable() {
        return resumeValue!=null;
    }

    public StepExecution getStep() {
        return step;
    }

    /*package*/ void setStep(StepExecution step) {
        this.step = step;
    }

    /**
     * Executes CPS code synchronously a little bit more, until it hits
     * the point the workflow needs to be dehydrated.
     */
    @SuppressWarnings("rawtypes")
    @NonNull Outcome runNextChunk() {
        assert program!=null;

        Outcome outcome;

        final CpsThread old = CURRENT.get();
        CURRENT.set(this);

        try (Timeout timeout = Timeout.limit(5, TimeUnit.MINUTES)) {
            LOGGER.fine(() -> "runNextChunk on " + resumeValue);
            final Outcome o = resumeValue;
            resumeValue = null;
            outcome = program.run0(o);
            if (outcome.getAbnormal() != null) {
                LOGGER.log(FINE, "ran and produced error", outcome.getAbnormal());
            } else {
                Outcome _outcome = outcome;
                LOGGER.fine(() -> "ran and produced " + _outcome);
            }

            if (outcome.getNormal() instanceof ThreadTask) {
                // if an execution in the thread safepoint is requested, deliver that
                ThreadTask sc = (ThreadTask) outcome.getNormal();
                ThreadTaskResult r = sc.eval(this);
                if (r.resume!=null) {
                    // yield, then keep evaluating the CPS code
                    resumeValue = r.resume;
                } else {
                    // break but with a different value
                    outcome = r.suspend;
                }
            }
        } finally {
            CURRENT.set(old);
        }

        if (promise!=null) {
            if (outcome.isSuccess())        promise.complete(outcome.getNormal());
            else {
                try {
                    promise.completeExceptionally(outcome.getAbnormal());
                } catch (Error e) {
                    if (e==outcome.getAbnormal()) {
                        // SettableFuture tries to rethrow an Error, which we don't want.
                        // so prevent that from happening. I need to see if this behaviour
                        // affects other places that use SettableFuture
                    } else {
                        throw e;
                    }
                }
            }
            promise = null;
        }

        return outcome;
    }

    /**
     * Does this thread still have something to execute?
     * (as opposed to have finished running, either normally or abnormally?)
     */
    boolean isAlive() {
        assert program != null; // Otherwise this CpsThread is not even part of the CpsThreadGroup, so how is it being accessed?
        return program.isResumable();
    }

    /**
     * When this thread is removed from its {@link CpsThreadGroup}, we null out most of its references in case
     * something is unexpectedly holding a reference directly to it (see JENKINS-63164 for an example scenario).
     */
    void cleanUp() {
        program = null;
        resumeValue = null;
        step = null;
        contextVariables = null;
        completionHandlers.clear();
    }

    @CpsVmThreadOnly
    void addCompletionHandler(FutureCallback<Object> h) {
        if (!(h instanceof Serializable))
            throw new IllegalArgumentException(h.getClass()+" is not serializable");
        completionHandlers.add(h);
    }

    @CpsVmThreadOnly
    void fireCompletionHandlers(Outcome o) {
        for (FutureCallback<Object> h : completionHandlers) {
            if (o.isSuccess())  h.onSuccess(o.getNormal());
            else                h.onFailure(o.getAbnormal());
        }
    }

    /**
     * Finds the next younger {@link CpsThread} that shares the same {@link FlowHead}.
     *
     * Cannot be {@code this}.
     */
    @CheckForNull CpsThread getNextInner() {
        for (CpsThread t : group.getThreads()) {
            if (t.id <= this.id) continue;
            if (t.head==this.head)  return t;
        }
        return null;
    }

    /**
     * Schedules the execution of this thread from the last {@linkplain Continuable#suspend(Object)} point.
     *
     * @return
     *      Future that promises the completion of the next {@link #runNextChunk()}.
     */
    public Future<Object> resume(Outcome v) {
        if (resumeValue != null) {
            return Futures.immediateFailedFuture(new IllegalStateException("Already resumed with " + resumeValue));
        }
        resumeValue = v;
        promise = new CompletableFuture<>();
        group.scheduleRun();
        return promise;
    }

    /**
     * Stops the execution of this thread. If it's paused to wait for the completion of {@link StepExecution},
     * call {@link StepExecution#stop(Throwable)} to give it a chance to clean up.
     *
     * <p>
     * If the execution is not inside a step (meaning it's paused in a safe point), then have the CPS thread
     * throw a given {@link Throwable} to break asap.
     */
    @CpsVmThreadOnly
    public void stop(Throwable t) {
        StepExecution s = getStep();  // this is the part that should run in CpsVmThread
        if (s == null) {
            // if it's not running inside a StepExecution, we need to set an interrupt flag
            // and interrupt at an earliest convenience
            Outcome o = new Outcome(null, t);
            if (resumeValue==null) {
                resume(o);
            } else {
                // this thread was already resumed, so just overwrite the value with a Throwable
                resumeValue = o;
            }
            return;
        }

        try (Timeout timeout = Timeout.limit(30, TimeUnit.SECONDS)) {
            s.stop(t);
        } catch (Exception e) {
            t.addSuppressed(e);
            s.getContext().onFailure(t);
        }
    }

    public List<StackTraceElement> getStackTrace() {
        Continuable p = program;
        if (p == null) {
            return List.of(new StackTraceElement("not", "running", null, -1));
        }
        return p.getStackTrace();
    }

    private static final Logger LOGGER = Logger.getLogger(CpsThread.class.getName());

    private static final long serialVersionUID = 1L;

    private static final ThreadLocal<CpsThread> CURRENT = new ThreadLocal<>();

    /**
     * While {@link CpsThreadGroup} executes, this method returns {@link CpsThread}
     * that's running.
     */
    @CpsVmThreadOnly
    public static CpsThread current() {
        return CURRENT.get();
    }

    @Override public String toString() {
        // getExecution().getOwner() would be useful but seems problematic.
        return "Thread #" + id + String.format(" @%h", this);
    }
}
