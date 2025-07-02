package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.Outcome;
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.FunctionCallEnv;
import com.cloudbees.groovy.cps.impl.TryBlockEnv;
import com.google.common.util.concurrent.FutureCallback;
import hudson.model.Action;
import hudson.util.Iterators;
import org.jenkinsci.plugins.workflow.actions.BodyInvocationAction;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.FailureHandler;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import net.jcip.annotations.GuardedBy;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearBlockHoppingScanner;

/**
 * {@link BodyExecution} impl for CPS.
 *
 * Instantiated when {@linkplain CpsBodyInvoker#start() the execution is scheduled},
 * and {@link CpsThreadGroup} gets updated with the new thread in the {@link #launch(CpsBodyInvoker, CpsThread, FlowHead)}
 * method, and this is the point in which the actual execution gest under way.
 *
 * <p>
 * This object is serializable while {@link CpsBodyInvoker} isn't.
 *
 * @author Kohsuke Kawaguchi
 * @see CpsBodyInvoker#start()
 */
@PersistIn(PersistenceContext.PROGRAM)
class CpsBodyExecution extends BodyExecution {
    /**
     * Thread that's executing the body.
     */
    @GuardedBy("this") // 'thread' and 'stopped' needs to be compared & set atomically
    private CpsThread thread;

    /**
     * Set to non-null if the body execution is stopped.
     */
    @GuardedBy("this")
    private Throwable stopped;

    private final List<BodyExecutionCallback> callbacks;

    /**
     * Context for the step who invoked its body.
     */
    private final CpsStepContext context;

    private String startNodeId;

    private final Continuation onSuccess = new SuccessAdapter();

    /**
     * Unlike {@link #onSuccess} that can only happen after {@link #launch(CpsBodyInvoker, CpsThread, FlowHead)},
     * a failure can happen right after {@link CpsBodyInvoker#start()} before we get a chance to be launched.
     */
    /*package*/ final Continuation onFailure = new FailureAdapter();

    @GuardedBy("this")
    private Outcome outcome;

    /**
     * If set, unexport this when the body closes.
     * When deserialized from old builds this will be null.
     * @see CpsBodyInvoker#unexport
     */
    private final @CheckForNull BodyReference bodyToUnexport;

    CpsBodyExecution(CpsStepContext context, List<BodyExecutionCallback> callbacks, @CheckForNull BodyReference bodyToUnexport) {
        this.context = context;
        this.callbacks = callbacks;
        this.bodyToUnexport = bodyToUnexport;
    }

    /**
     * Starts evaluating the body.
     *
     * If the body is a synchronous closure, this method evaluates the closure synchronously.
     * Otherwise, the body is asynchronous and the method schedules another thread to evaluate the body.
     *
     * @param currentThread
     *      The thread whose context the new thread will inherit.
     */
    @CpsVmThreadOnly
    /*package*/ void launch(CpsBodyInvoker params, CpsThread currentThread, FlowHead head) {
        if (isLaunched())
            throw new IllegalStateException("Already launched");

        StepStartNode sn = addBodyStartFlowNode(head);
        for (Action a : params.startNodeActions) {
            if (a!=null)
                sn.addAction(a);
        }

        head.setNewHead(sn);

        StepContext sc = new CpsBodySubContext(context, sn);
        for (BodyExecutionCallback c : callbacks) {
            c.onStart(sc);
        }

        try {
            // TODO: handle arguments to closure
            Object x = params.body.getBody(currentThread).call();

            // body has completed synchronously. mark this done after the fact
            // pointless synchronization to make findbugs happy. This is already done, so there's no cancelling this anyway.
            synchronized (this) {
                this.thread = currentThread;
            }
            onSuccess.receive(x);
        } catch (CpsCallableInvocation e) {
            // execute this closure asynchronously
            // TODO: does it make sense that the new thread shares the same head?
            CpsThread t = currentThread.group.addThread(createContinuable(currentThread, e), head,
                    ContextVariableSet.from(currentThread.getContextVariables(), params.contextOverrides));

            // let the new CpsThread run. Either get the new thread going normally with (null,null), or abort from the beginning
            // due to earlier cancellation
            synchronized (this) {
                t.resume(new Outcome(null, stopped));
                assert this.thread==null;
                this.thread = t;
            }
        } catch (Throwable t) {
            // body has completed synchronously and abnormally
            synchronized (this) {
                this.thread = currentThread;
            }
            onFailure.receive(t);
        }
    }

    /**
     * Creates {@link Continuable} that executes the given invocation and pass its result to {@link FutureCallback}.
     *
     * The {@link Continuable} itself will just yield null. {@link CpsThreadGroup} considers the whole
     * execution a failure if any of the threads fail, so this behaviour ensures that a problem in the closure
     * body won't terminate the workflow.
     */
    private Continuable createContinuable(CpsThread currentThread, CpsCallableInvocation inv) {
        // we need FunctionCallEnv that acts as the back drop of try/catch block.
        // TODO: we need to capture the surrounding calling context to capture variables, and switch to ClosureCallEnv

        FunctionCallEnv caller = new FunctionCallEnv(null, onSuccess, null, null);
        caller.setInvoker(currentThread.getExecution().createInvoker());

        // catch an exception thrown from body and treat that as a failure
        TryBlockEnv env = new TryBlockEnv(caller, null);
        env.addHandler(Throwable.class, onFailure);

        return new Continuable(
            // this source location is a place holder for the step implementation.
            // perhaps at some point in the future we'll let the Step implementation control this.
            inv.invoke(env, null, onSuccess));
    }

    @Override
    public Collection<StepExecution> getCurrentExecutions() {
        CpsThread t;
        synchronized (this) {
            t = thread;
            if (t == null) {
                return Collections.emptySet();
            }
        }
        final CompletableFuture<Collection<StepExecution>> result = new CompletableFuture<>();
        t.getExecution().runInCpsVmThread(new FutureCallback<>() {
            @Override public void onSuccess(CpsThreadGroup g) {
                try {
                    List<StepExecution> executions = new ArrayList<>();
                    // cf. trick in CpsFlowExecution.getCurrentExecutions(true)
                    Map<FlowHead, CpsThread> m = new LinkedHashMap<>();
                    for (CpsThread t : g.getThreads()) {
                        m.put(t.head, t);
                    }
                    for (CpsThread t : m.values()) {
                        // TODO seems cumbersome to have to go through the flow graph to find out whether a head is a descendant of ours, yet FlowHead does not seem to retain a parent field
                        LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();
                        scanner.setup(t.head.get());
                        for (FlowNode node : scanner) {
                            if (node.getId().equals(startNodeId)) {
                                // this head is inside this body execution
                                StepExecution execution = t.getStep();
                                if (execution != null) {
                                    executions.add(execution);
                                }
                                break;
                            }
                        }
                    }
                    result.complete(executions);
                } catch (Exception x) {
                    result.completeExceptionally(x);
                }
            }
            @Override public void onFailure(Throwable t) {
                result.completeExceptionally(t);
            }
        });
        try {
            return result.get(1, TimeUnit.MINUTES);
        } catch (ExecutionException | InterruptedException | TimeoutException x) {
            // TODO access to CpsThreadGroup.threads must be restricted to the CPS VM thread, but the API signature does not allow us to return a ListenableFuture or throw checked exceptions
            throw new RuntimeException(x);
        }
    }

    @Override
    public boolean cancel(Throwable error) {
        // 'stopped' and 'thread' are updated atomically
        CpsThread t;
        synchronized (this) {
            if (isDone())  return false;   // already complete
            stopped = error;
            t = this.thread;
        }

        if (t!=null) {
            t.getExecution().runInCpsVmThread(new FutureCallback<>() {
                @Override
                public void onSuccess(CpsThreadGroup g) {
                    if (thread == null) {
                        return;
                    }
                    // Similar to getCurrentExecutions but we want the raw CpsThread, not a StepExecution; cf. CpsFlowExecution.interrupt
                    Map<FlowHead, CpsThread> m = new LinkedHashMap<>();
                    for (CpsThread t : thread.group.getThreads()) {
                        m.put(t.head, t);
                    }
                    for (CpsThread t : Iterators.reverse(List.copyOf(m.values()))) {
                        LinearBlockHoppingScanner scanner = new LinearBlockHoppingScanner();
                        scanner.setup(t.head.get());
                        for (FlowNode node : scanner) {
                            if (node.getId().equals(startNodeId)) {
                                t.stop(stopped);
                                break;
                            }
                        }
                    }
                }

                @Override
                public void onFailure(Throwable t) {
                    t.addSuppressed(error);
                    LOGGER.log(Level.WARNING, "could not cancel " + context, t);
                }
            });
        } else {
            // if it hasn't begun executing, we'll stop it when
            // it begins.
        }
        return true;
    }

    @Override
    public synchronized boolean isCancelled() {
        return stopped!=null && isDone();
    }

    /**
     * Is the execution under way? True after {@link #launch(CpsBodyInvoker, CpsThread, FlowHead)}
     */
    public synchronized boolean isLaunched() {
        return thread!=null;
    }

    @Override
    public synchronized Object get() throws InterruptedException, ExecutionException {
        while (outcome==null) {
            wait();
        }
        if (outcome.isSuccess())    return outcome.getNormal();
        else    throw new ExecutionException(outcome.getAbnormal());
    }

    @Override
    public synchronized Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        long endTime = System.currentTimeMillis() + unit.toMillis(timeout);
        long remaining;
        while (outcome==null && (remaining=endTime-System.currentTimeMillis()) > 0) {
            wait(remaining);
        }

        if (outcome==null)
            throw new TimeoutException();

        if (outcome.isSuccess())    return outcome.getNormal();
        else    throw new ExecutionException(outcome.getAbnormal());
    }

    private void setOutcome(Outcome o) {
        synchronized (this) {
            if (bodyToUnexport != null && thread != null) {
                thread.group.unexport(bodyToUnexport);
            }
            if (outcome!=null)
                throw new IllegalStateException("Outcome is already set");
            this.outcome = o;
            notifyAll();    // wake up everyone waiting for the outcome.
        }
        context.saveState();
    }

    public synchronized boolean isDone() {
        return outcome!=null;
    }

    private class FailureAdapter implements Continuation {
        @Override
        public Next receive(Object o) {
            if (!isLaunched()) {
                // failed before we even started. fake the start node that start() would have created.
                FlowHead h = CpsThread.current().head;
                StepStartNode ssn = addBodyStartFlowNode(h);
                h.setNewHead(ssn);
            }
            StepEndNode en = addBodyEndFlowNode();
            Throwable t = (Throwable)o;

            var sc = new CpsBodySubContext(context, en);
            t = handleError(sc, t);

            en.addAction(new ErrorAction(t));
            CpsFlowExecution.maybeAutoPersistNode(en);

            setOutcome(new Outcome(null,t));
            for (BodyExecutionCallback c : callbacks) {
                try {
                    c.onFailure(sc, t);
                } catch (Exception e) {
                    t.addSuppressed(e);
                    sc.onFailure(t);
                }
            }
            synchronized (CpsBodyExecution.this) {
                thread = null;
            }
            return Next.terminate(null);
        }

        private Throwable handleError(CpsBodySubContext sc, Throwable t) {
            CpsThread localThread;
            synchronized (CpsBodyExecution.this) {
                localThread = thread;
            }
            try {
                var handler = localThread.getContextVariables().get(FailureHandler.class, localThread::getExecution, sc::getNode);
                if (handler != null) {
                    return handler.handle(sc, t);
                }
            } catch (Throwable t2) {
                t.addSuppressed(t2);
            }
            return t;
        }

        private static final long serialVersionUID = 1L;
    }

    private class SuccessAdapter implements Continuation {
        @Override
        public Next receive(Object o) {
            StepEndNode en = addBodyEndFlowNode();
            CpsFlowExecution.maybeAutoPersistNode(en);
            setOutcome(new Outcome(o,null));
            StepContext sc = new CpsBodySubContext(context, en);
            for (BodyExecutionCallback c : callbacks) {
                try {
                    c.onSuccess(sc, o);
                } catch (Exception e) {
                    sc.onFailure(e);
                }
            }
            synchronized (CpsBodyExecution.this) {
                thread = null;
            }
            return Next.terminate(null);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Inserts the flow node that indicates the beginning of the body invocation.
     *
     * @see #addBodyEndFlowNode()
     */
    private @NonNull StepStartNode addBodyStartFlowNode(FlowHead head) {
        CpsFlowExecution.maybeAutoPersistNode(head.get());
        StepStartNode start = new StepStartNode(head.getExecution(),
                context.getStepDescriptor(), head.get());
        head.getExecution().cacheNode(start);
        this.startNodeId = start.getId();
        start.addAction(new BodyInvocationAction());
        return start;
    }

    /**
     * Inserts the flow node that indicates the beginning of the body invocation.
     *
     * @see #addBodyStartFlowNode(FlowHead)
     */
    private @NonNull StepEndNode addBodyEndFlowNode() {
        try {
            FlowHead head = CpsThread.current().head;

            StepEndNode end = new StepEndNode(head.getExecution(),
                    getBodyStartNode(), head.get());
            head.getExecution().cacheNode(end);
            end.addAction(new BodyInvocationAction());
            head.setNewHead(end);

            return end;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to grow the flow graph", e);
            throw new Error(e);
        }
    }

    public StepStartNode getBodyStartNode() throws IOException {
        if (startNodeId==null)
            throw new IllegalStateException("StepStartNode is not yet created");
        CpsThread t;
        synchronized (this) {// to make findbugs happy
            t = thread;
        }
        return (StepStartNode) t.getExecution().getNode(startNodeId);
    }


    private static final long serialVersionUID = 1L;

    private static final Logger LOGGER = Logger.getLogger(CpsBodyExecution.class.getName());
}
