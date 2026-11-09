package org.jenkinsci.plugins.workflow.cps;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
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
}
