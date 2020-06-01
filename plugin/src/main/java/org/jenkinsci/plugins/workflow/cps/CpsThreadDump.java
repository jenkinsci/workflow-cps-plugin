package org.jenkinsci.plugins.workflow.cps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

/**
 * Immutable snapshot of a state of {@link CpsThreadGroup}.
 *
 * @author Kohsuke Kawaguchi
 */
public final class CpsThreadDump {

    private final List<ThreadInfo> threads = new ArrayList<>();

    public static final class ThreadInfo {
        private final String headline;
        private final List<StackTraceElement> stack = new ArrayList<>();

        /**
         * Given a list of {@link CpsThread}s that share the same {@link FlowHead}, in the order
         * from outer to inner, reconstruct the thread stack.
         */
        private ThreadInfo(List<CpsThread> e) {
            CpsThread head = e.get(e.size() - 1);
            headline = "Thread #" + head.id;

            ListIterator<CpsThread> itr = e.listIterator(e.size());
            while (itr.hasPrevious()) {
                CpsThread t = itr.previous();

                StepExecution s = t.getStep();
                if (s !=null) {
                    StepDescriptor d = ((CpsStepContext) s.getContext()).getStepDescriptor();
                    if (d != null) {
                        String status = s.getStatusBounded(3, TimeUnit.SECONDS);
                        if (status != null) {
                            stack.add(new StackTraceElement("DSL", d.getFunctionName(), status, -1));
                        } else {
                            stack.add(new StackTraceElement("DSL", d.getFunctionName(), null, -2));
                        }
                    }
                }
                stack.addAll(t.getStackTrace());
            }
        }

        /**
         * Create a fake {@link ThreadInfo} from a {@link Throwable} that copies its
         * stack trace history.
         */
        public ThreadInfo(Throwable t) {
            headline = t.toString();
            stack.addAll(Arrays.asList(t.getStackTrace()));
        }

        /**
         * Can be empty but never be null. First element is the top of the stack.
         */
        public List<StackTraceElement> getStackTrace() {
            return Collections.unmodifiableList(stack);
        }

        public String getHeadline() {
            return headline;
        }

        public void print(PrintWriter w) {
            w.println(headline);
            for (StackTraceElement e : stack) {
                w.println("\tat " + e);
            }
        }

        @Override
        public String toString() {
            StringWriter sw = new StringWriter();
            print(new PrintWriter(sw));
            return sw.toString();
        }
    }

    /**
     * Use one of the {@link #from(CpsThreadGroup)} method.
     */
    private CpsThreadDump() {
    }

    public List<ThreadInfo> getThreads() {
        return Collections.unmodifiableList(threads);
    }

    @SuppressFBWarnings(value="DM_DEFAULT_ENCODING", justification="Only used by tests anyway.")
    public void print(PrintStream ps) {
        print(new PrintWriter(ps,true));
    }

    public void print(PrintWriter w) {
        for (ThreadInfo t : threads)
            t.print(w);
    }

    @Override
    public String toString() {
        StringWriter sw = new StringWriter();
        print(new PrintWriter(sw));
        return sw.toString();
    }

    public static CpsThreadDump from(Throwable t) {
        CpsThreadDump td = new CpsThreadDump();
        td.threads.add(new ThreadInfo(t));
        return td;}

    public static CpsThreadDump from(CpsThreadGroup g) {
        // all the threads that share the same head form a logically single thread
        Map<FlowHead, List<CpsThread>> m = new LinkedHashMap<>();
        for (CpsThread t : g.getThreads()) {
            List<CpsThread> l = m.get(t.head);
            if (l==null)    m.put(t.head, l = new ArrayList<>());
            l.add(t);
        }

        CpsThreadDump td = new CpsThreadDump();
        for (List<CpsThread> e : m.values())
            td.threads.add(new ThreadInfo(e));
        return td;
    }

    /**
     * A mock thread dump that merely displays some fixed text.
     * @param text possibly multiline string
     */
    @SuppressWarnings("serial")
    public static @Nonnull CpsThreadDump fromText(@Nonnull final String text) {
        return CpsThreadDump.from(new Throwable() {
            @Override public String toString() {
                return text;
            }
            @Override public Throwable fillInStackTrace() {
                return this; // irrelevant
            }
        });
    }

    /**
     * Constant that indicates everything is done and no thread is alive.
     */
    public static final CpsThreadDump EMPTY = new CpsThreadDump();

    @Deprecated
    public static final CpsThreadDump UNKNOWN = fromText("Program state is not yet known");

}
