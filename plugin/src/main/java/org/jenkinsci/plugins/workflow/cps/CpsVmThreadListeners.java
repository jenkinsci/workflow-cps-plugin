package org.jenkinsci.plugins.workflow.cps;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.graph.FlowNode;

/**
 * Calls the {@link CpsVmThreadListener}s and closes what they return. A listener that throws is logged and skipped: the
 * work of the execution goes on.
 */
final class CpsVmThreadListeners {
    private static final Logger LOGGER = Logger.getLogger(CpsVmThreadListener.class.getName());

    private CpsVmThreadListeners() {}

    @NonNull
    static List<AutoCloseable> aroundTask(@NonNull CpsFlowExecution execution) {
        return call(listener -> listener.aroundTask(execution), () -> "a CPS VM task of " + execution);
    }

    @NonNull
    static List<CpsVmThreadListener.ProgramRun> aroundProgramRun(
            @NonNull CpsFlowExecution execution, @CheckForNull FlowNode head) {
        return call(listener -> listener.aroundProgramRun(execution, head), () -> "a program run of " + execution);
    }

    @NonNull
    static List<AutoCloseable> aroundAsyncNotification(@NonNull CpsFlowExecution execution, @NonNull FlowNode node) {
        return call(
                listener -> listener.aroundAsyncNotification(execution, node),
                () -> "the notification of " + node + " in " + execution);
    }

    static void onNewHead(@NonNull List<CpsVmThreadListener.ProgramRun> runs, @NonNull FlowNode head) {
        for (CpsVmThreadListener.ProgramRun run : runs) {
            try {
                run.onNewHead(head);
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, t, () -> run + " failed on the new head " + head);
            }
        }
    }

    /** Closes the objects that listeners returned, the last one first. */
    static void close(@NonNull List<? extends AutoCloseable> opened, @NonNull Supplier<String> what) {
        for (int i = opened.size() - 1; i >= 0; i--) {
            AutoCloseable closeable = opened.get(i);
            try {
                closeable.close();
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, t, () -> "failed to close " + closeable + " after " + what.get());
            }
        }
    }

    @NonNull
    private static <T> List<T> call(@NonNull Function<CpsVmThreadListener, T> around, @NonNull Supplier<String> what) {
        if (Jenkins.getInstanceOrNull() == null) {
            return List.of();
        }
        List<T> opened = new ArrayList<>();
        for (CpsVmThreadListener listener : ExtensionList.lookup(CpsVmThreadListener.class)) {
            try {
                T closeable = around.apply(listener);
                if (closeable != null) {
                    opened.add(closeable);
                }
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, t, () -> listener + " failed around " + what.get());
            }
        }
        return opened;
    }
}
