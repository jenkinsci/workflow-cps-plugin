package org.jenkinsci.plugins.workflow.cps;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

/**
 * Lets a plugin set up thread-local state, such as a tracing context, for the work that a CPS VM thread does for one
 * {@link CpsFlowExecution}, and only for that work. The CPS VM runs the work of all executions on one pool of threads:
 * the thread closes what a listener returned before it goes on with other work, of this execution or of another one.
 * <p>
 * Each task of an execution goes through {@link #aroundTask}. In a task, each run of the program of one of the
 * execution's {@link CpsThread}s, until that thread waits or ends, goes through {@link #aroundProgramRun}. The objects
 * that listeners return are closed in the reverse order, on the same thread, whether the work returns or throws.
 *
 * @since TODO
 */
public interface CpsVmThreadListener extends ExtensionPoint {

    /**
     * Called on the CPS VM thread before it runs a task of the execution: runs of its program, a notification of the
     * {@link GraphListener}s that are not {@link GraphListener.Synchronous}, or other work that the execution does in
     * the CPS VM. That thread closes the returned object once the task returns or throws.
     *
     * @return what to close after the task, or {@code null}
     */
    @CheckForNull
    default AutoCloseable aroundTask(@NonNull CpsFlowExecution execution) {
        return null;
    }

    /**
     * Called on the CPS VM thread, in a task, before it runs the program of one {@link CpsThread} of the execution until
     * that thread waits or ends. That thread notifies the returned object of the new heads of that {@link CpsThread}
     * during the run, and closes it once the run returns or throws.
     *
     * @param head the node where the program of that thread is, or {@code null} if it has none yet
     * @return what to notify and close, or {@code null}
     */
    @CheckForNull
    default ProgramRun aroundProgramRun(@NonNull CpsFlowExecution execution, @CheckForNull FlowNode head) {
        return null;
    }

    /** What a listener sets up for one run of the program of a {@link CpsThread}. */
    interface ProgramRun extends AutoCloseable {

        /**
         * Called on the CPS VM thread when the program that runs moves the head of its {@link CpsThread} to a new node,
         * after the {@link GraphListener.Synchronous} listeners have been notified of that node. Not called for the
         * heads of other threads, such as the branches that a {@code parallel} step starts.
         */
        default void onNewHead(@NonNull FlowNode head) {}
    }
}
