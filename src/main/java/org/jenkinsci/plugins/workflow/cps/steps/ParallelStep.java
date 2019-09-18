package org.jenkinsci.plugins.workflow.cps.steps;

import com.cloudbees.groovy.cps.Outcome;

import groovy.lang.Closure;
import hudson.AbortException;
import hudson.Extension;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.io.IOException;

import org.jenkinsci.plugins.workflow.cps.CpsVmThreadOnly;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;

/**
 * CPS-specific {@link Step} implementation that executes multiple closures in parallel.
 *
 * TODO: somehow needs to declare that this only works with CpsFlowExecution.
 *
 * @author Kohsuke Kawaguchi
 */
public class ParallelStep extends Step {

    private static final Logger LOGGER = Logger.getLogger(ParallelStep.class.getName());

    /** should a failure in a parallel branch terminate other still executing branches. */
    private final boolean failFast;

    /** Whether or not the worst failure in a parallel branch should be propagated when joining. **/
    private final boolean propagateWorst;

    /**
     * All the sub-workflows as {@link Closure}s, keyed by their names.
     */
    /*package*/ final transient Map<String,Closure> closures;

    public ParallelStep(Map<String, Closure> closures, boolean failFast, boolean propagateWorst) {
        this.closures = closures;
        this.failFast = failFast;
        this.propagateWorst = propagateWorst;
    }

    /** @deprecated Use {@link #ParallelStep(Map, boolean, boolean)} in typical cases. */
    @Deprecated
    public ParallelStep(Map<String,Closure> closures, boolean failFast) {
        this.closures = closures;
        this.failFast = failFast;
        this.propagateWorst = false;
    }

    @Override
    @CpsVmThreadOnly("CPS program calls this, which is run by CpsVmThread")
    public StepExecution start(StepContext context) throws Exception {
        return new ParallelStepExecution(this, context);
    }

    /*package*/ boolean isFailFast() {
        return failFast;
    }

    /*package*/ boolean isPropagateWorst() {
        return propagateWorst;
    }

    @PersistIn(PROGRAM)
    static class ResultHandler implements Serializable {
        private final StepContext context;
        private final ParallelStepExecution stepExecution;
        private final boolean failFast;
        private final boolean propagateWorst;
        /** Have we called stop on the StepExecution? */
        private boolean stopSent = false;
        /** If we fail fast, we need to record the first failure. Use a linked hash set to maintain insertion order. */
        private final LinkedHashSet<Throwable> failures = new LinkedHashSet<>();

        /**
         * Collect the results of sub-workflows as they complete.
         * The key set is fully populated from the beginning.
         */
        private final Map<String,Outcome> outcomes = new HashMap<String, Outcome>();

        ResultHandler(
                StepContext context,
                ParallelStepExecution parallelStepExecution,
                boolean failFast,
                boolean propagateWorst) {
            this.context = context;
            this.stepExecution = parallelStepExecution;
            this.failFast = failFast;
            this.propagateWorst = propagateWorst;
        }

        Callback callbackFor(String name) {
            outcomes.put(name, null);
            return new Callback(this, name);
        }

        private void stopSent() {
            stopSent = true;
        }

        private boolean isStopSent() {
            return stopSent;
        }

        private static class Callback extends BodyExecutionCallback {

            private final ResultHandler handler;
            private final String name;

            Callback(ResultHandler handler, String name) {
                this.handler = handler;
                this.name = name;
            }

            @Override
            public void onSuccess(StepContext context, Object result) {
                handler.outcomes.put(name, new Outcome(result, null));
                checkAllDone(false);
            }

            @Override
            public void onFailure(StepContext context, Throwable t) {
                handler.outcomes.put(name, new Outcome(null, t));
                try {
                    context.get(TaskListener.class).getLogger().println("Failed in branch " + name);
                } catch (IOException | InterruptedException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
                /*
                 * Use a set because we may be encountering the same abort being delivered across
                 * branches.
                 */
                handler.failures.add(t);
                checkAllDone(true);
            }

            private void checkAllDone(boolean stepFailed) {
                Map<String,Object> success = new HashMap<String, Object>();
                for (Entry<String,Outcome> e : handler.outcomes.entrySet()) {
                    Outcome o = e.getValue();

                    if (o==null) {
                        // some of the results are not yet ready
                        if (stepFailed && handler.failFast && ! handler.isStopSent()) {
                            handler.stopSent();
                            try {
                                handler.stepExecution.stop(new FailFastException());
                            }
                            catch (Exception ignored) {
                                // ignored.
                            }
                        }
                        return;
                    }
                    if (o.isFailure()) {
                        if (handler.failures.isEmpty()) {
                            // in case the plugin is upgraded whilst a parallel step is running
                            handler.failures.add(e.getValue().getAbnormal());
                        }
                        // recorded in the onFailure
                    } else {
                        success.put(e.getKey(), o.getNormal());
                    }
                }
                // all done
                List<Throwable> toAttach = new ArrayList<>(handler.failures);
                if (handler.propagateWorst) {
                    Collections.sort(toAttach, new ThrowableComparator(handler.failures));
                }
                if (!toAttach.isEmpty()) {
                    Throwable head = toAttach.get(0);
                    for (int i = 1; i < toAttach.size(); i++) {
                        head.addSuppressed(toAttach.get(i));
                    }
                    handler.context.onFailure(head);
                } else {
                    handler.context.onSuccess(success);
                }
            }
            
            private static final long serialVersionUID = 1L;
        }

        static final class ThrowableComparator implements Comparator<Throwable>, Serializable {

            private final LinkedHashSet<Throwable> insertionOrder;

            ThrowableComparator() {
                this.insertionOrder = new LinkedHashSet<>();
            }

            ThrowableComparator(LinkedHashSet<Throwable> insertionOrder) {
                this.insertionOrder = insertionOrder;
            }

            @Override
            public int compare(Throwable t1, Throwable t2) {
                if (!(t1 instanceof FlowInterruptedException)
                        && t2 instanceof FlowInterruptedException) {
                    // FlowInterruptedException is always higher than any other exception.
                    return -1;
                } else if (t1 instanceof FlowInterruptedException
                        && !(t2 instanceof FlowInterruptedException)) {
                    // FlowInterruptedException is always higher than any other exception.
                    return 1;
                } else if (!(t1 instanceof AbortException) && t2 instanceof AbortException) {
                    // AbortException is always higher than any exception other than
                    // FlowInterruptedException.
                    return -1;
                } else if (t1 instanceof AbortException && !(t2 instanceof AbortException)) {
                    // AbortException is always higher than any exception other than
                    // FlowInterruptedException.
                    return 1;
                } else if (t1 instanceof FlowInterruptedException
                        && t2 instanceof FlowInterruptedException) {
                    // Two FlowInterruptedExceptions are compared by their results.
                    FlowInterruptedException fie1 = (FlowInterruptedException) t1;
                    FlowInterruptedException fie2 = (FlowInterruptedException) t2;
                    Result r1 = fie1.getResult();
                    Result r2 = fie2.getResult();
                    if (r1.isWorseThan(r2)) {
                        return -1;
                    } else if (r1.isBetterThan(r2)) {
                        return 1;
                    }
                } else if (insertionOrder.contains(t1) && insertionOrder.contains(t2)) {
                    // Break ties by insertion order. Earlier errors are worse.
                    List<Throwable> insertionOrderList = new ArrayList<>(insertionOrder);
                    int index1 = insertionOrderList.indexOf(t1);
                    int index2 = insertionOrderList.indexOf(t2);
                    if (index1 < index2) {
                        return -1;
                    } else if (index1 > index2) {
                        return 1;
                    }
                }
                return 0;
            }
        }

        private static final long serialVersionUID = 1L;
    }

    /** Internal exception that is only used internally to abort a parallel body in the case of a failFast body failing. */
    private static final class FailFastException extends Exception {
        private static final long serialVersionUID = 1L;
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {
        private static final String FAIL_FAST_FLAG = "failFast";
        private static final String PROPAGATE_WORST_FLAG = "propagateWorst";

        @Override
        public String getFunctionName() {
            return "parallel";
        }

        @Override
        public Step newInstance(Map<String,Object> arguments) {
            boolean failFast = false;
            boolean propagateWorst = false;
            Map<String,Closure<?>> closures = new LinkedHashMap<String, Closure<?>>();
            for (Entry<String,Object> e : arguments.entrySet()) {
                if ((e.getValue() instanceof Closure)) {
                    closures.put(e.getKey(), (Closure<?>)e.getValue());
                }
                else if (FAIL_FAST_FLAG.equals(e.getKey()) && e.getValue() instanceof Boolean) {
                    failFast = (Boolean)e.getValue();
                } else if (PROPAGATE_WORST_FLAG.equals(e.getKey())
                        && e.getValue() instanceof Boolean) {
                    propagateWorst = (Boolean) e.getValue();
                }
                else {
                    throw new IllegalArgumentException(
                            "Expected a closure, failFast, or propagateWorst but found "
                                    + e.getKey()
                                    + "="
                                    + e.getValue());
                }
                if (failFast && propagateWorst) {
                    throw new IllegalArgumentException(
                            "failFast and propagateWorst are mutually exclusive");
                }
            }
            return new ParallelStep((Map) closures, failFast, propagateWorst);
        }

        @Override public Map<String,Object> defineArguments(Step step) throws UnsupportedOperationException {
            ParallelStep ps = (ParallelStep) step;
            Map<String,Object> retVal = new TreeMap<String,Object>(ps.closures);
            if (ps.failFast) {
                retVal.put(FAIL_FAST_FLAG, Boolean.TRUE);
            }
            if (ps.propagateWorst) {
                retVal.put(PROPAGATE_WORST_FLAG, Boolean.TRUE);
            }
            return retVal;
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return Collections.<Class<?>>singleton(TaskListener.class);
        }

        /**
         * Block arguments would have to be wrapped into a list and passed as such.
         * It doesn't make sense to do the following as it is single-thread:
         *
         * <pre>
         * parallel {
         *      foo();
         * }
         * </pre>
         */
        @Override
        public boolean takesImplicitBlockArgument() {
            return false;
        }

        @Override
        public String getDisplayName() {
            return "Execute in parallel";
        }
    }
}
