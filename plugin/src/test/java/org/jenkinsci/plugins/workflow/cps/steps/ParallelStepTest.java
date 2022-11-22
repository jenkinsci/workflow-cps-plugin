package org.jenkinsci.plugins.workflow.cps.steps;

import hudson.AbortException;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Result;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;

import static java.util.Arrays.asList;

import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;
import jenkins.model.CauseOfInterruption;

import org.apache.commons.io.IOUtils;
import org.jenkinsci.plugins.workflow.SingleJobTestBase;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.ThreadNameAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsThreadGroup;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.EchoStep;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.durable_task.BatchScriptStep;
import org.jenkinsci.plugins.workflow.steps.durable_task.ShellStep;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable;
import org.jenkinsci.plugins.workflow.support.visualization.table.FlowGraphTable.Row;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.WithoutJenkins;

/**
 * Tests for {@link ParallelStep}.
 *
 * @author Kohsuke Kawaguchi
 */
public class ParallelStepTest extends SingleJobTestBase {

    @Rule public LoggerRule logging = new LoggerRule();

    private FlowGraphTable t;

    /**
     * The first baby step.
     */
    @Test
    public void minimumViableParallelRun() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                logging.record(CpsThreadGroup.class, Level.FINE);
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "node {",
                    "  x = parallel( a: { echo('echo a'); return 1; }, b: { echo('echo b'); sleep 1; return 2; } )",
                    "  assert x.a==1",
                    "  assert x.b==2",
                    "}"
                ), false));

                startBuilding().get(); // 15, SECONDS);
                assertBuildCompletedSuccessfully();

                buildTable();
                shouldHaveParallelStepsInTheOrder("a", "b");
            }
        });
    }

    /**
     * Failure in a branch will cause the join to fail.
     */
    @Test
    public void failure_in_subflow_will_cause_join_to_fail() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "import "+AbortException.class.getName(),

                    "node {",
                    "  try {",
                    "    parallel(",
                    "      b: { error 'died' },",

                        // make sure this branch takes longer than a
                    "      a: { sleep 3; writeFile text: '', file: 'a.done' }",
                    "    )",
                    "    assert false;",
                    "  } catch (AbortException e) {",
                    "    assert e.message == 'died'",
                    "  }",
                    "}"
                ), false));

                startBuilding().get();
                assertBuildCompletedSuccessfully();
                assert jenkins().getWorkspaceFor(p).child("a.done").exists();

                buildTable();
                shouldHaveParallelStepsInTheOrder("b","a");
            }
        });
    }


    /**
     * Failure in a branch will cause the join to fail.
     */
    @Test @Issue("JENKINS-26034")
    public void failure_in_subflow_will_fail_fast() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "import "+AbortException.class.getName(),

                    "node {",
                    "  try {",
                    "    parallel(",
                    "      b: { error 'died' },",

                        // make sure this branch takes longer than a
                    "      a: { sleep 25; writeFile text: '', file: 'a.done' },",
                    "      failFast: true",
                    "    )",
                    "    assert false",
                    "  } catch (AbortException e) {",
                    "    assert e.message == 'died'",
                    "  }",
                    "}"
                ), false));

                startBuilding().get();
                assertBuildCompletedSuccessfully();
                Assert.assertFalse("a should have aborted", jenkins().getWorkspaceFor(p).child("a.done").exists());
                for (FlowNode n : new DepthFirstScanner().allNodes(e)) {
                    ErrorAction err = n.getPersistentAction(ErrorAction.class);
                    if (err != null) {
                        if (err.getError() instanceof FlowInterruptedException) {
                            assertEquals("Failed in branch b", ((FlowInterruptedException) err.getError()).getCauses().stream().map(CauseOfInterruption::getShortDescription).collect(Collectors.joining("; ")));
                        }
                    }
                }
            }
        });
    }

    /**
     * FailFast should not kill branches if there is no failure.
     */
    @Test @Issue("JENKINS-26034")
    public void failFast_has_no_effect_on_success() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "node {",
                    "    parallel(",
                    "      a: { echo 'hello from a';sleep 1;echo 'goodbye from a' },",
                    "      b: { echo 'hello from b';sleep 1;echo 'goodbye from b' },",
                    "      c: { echo 'hello from c';sleep 1;echo 'goodbye from c' },",
                    // make sure this branch is quicker than the others.
                    "      d: { echo 'hello from d' },",
                    "      failFast: true",
                    "    )",
                    "}"
                ), false));

                startBuilding().get();
                assertBuildCompletedSuccessfully();
            }
        });
    }

    @Issue("JENKINS-25894")
    @Test public void failureReporting() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("parallel a: {semaphore 'a'}, b: {semaphore 'b'}", true));
                // Original bug report: AbortException not properly handled.
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.success("a/1", null);
                SemaphoreStep.failure("b/1", new AbortException("normal failure"));
                story.j.assertBuildStatus(Result.FAILURE, story.j.waitForCompletion(b1));
                story.j.assertLogContains("Failed in branch b", b1);
                // Apparently !b1.isBuilding() before WorkflowRun.finish has printed the stack trace, so need to wait for StreamBuildListener.finished:
                story.j.waitForMessage("Finished: FAILURE", b1);
                story.j.assertLogContains("normal failure", b1);
                story.j.assertLogNotContains("AbortException", b1);
                // Other exceptions should include a stack trace.
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.success("a/2", null);
                SemaphoreStep.failure("b/2", new IllegalStateException("ouch"));
                story.j.assertBuildStatus(Result.FAILURE, story.j.waitForCompletion(b2));
                story.j.assertLogContains("Failed in branch b", b2);
                story.j.waitForMessage("Finished: FAILURE", b2);
                story.j.assertLogContains("java.lang.IllegalStateException: ouch", b2);
                story.j.assertLogContains("\tat " + ParallelStepTest.class.getName(), b2);
                // If multiple branches fail, we want to see all the stack traces.
                WorkflowRun b3 = p.scheduleBuild2(0).waitForStart();
                try {
                    failureInA();
                    fail();
                } catch (IllegalStateException x) {
                    SemaphoreStep.failure("a/3", x);
                }
                try {
                    failureInB();
                    fail();
                } catch (IllegalStateException x) {
                    SemaphoreStep.failure("b/3", x);
                }
                story.j.assertBuildStatus(Result.FAILURE, story.j.waitForCompletion(b3));
                story.j.assertLogContains("Failed in branch a", b3);
                story.j.assertLogContains("Failed in branch b", b3);
                story.j.waitForMessage("Finished: FAILURE", b3);
                story.j.assertLogContains("java.lang.IllegalStateException: first problem", b3);
                story.j.assertLogContains("\tat " + ParallelStepTest.class.getName() + ".failureInA", b3);
                story.j.assertLogContains("java.lang.IllegalStateException: second problem", b3);
                story.j.assertLogContains("\tat " + ParallelStepTest.class.getName() + ".failureInB", b3);
                // Also check stack traces within the script.
                p.setDefinition(new CpsFlowDefinition(
                    "parallel bad: {\n" +
                    "  throw new IllegalStateException('bad')\n" +
                    "}", false));
                WorkflowRun b4 = story.j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
                story.j.assertLogContains("Failed in branch bad", b4);
                story.j.waitForMessage("Finished: FAILURE", b4);
                story.j.assertLogContains("java.lang.IllegalStateException: bad", b4);
                story.j.assertLogContains("\tat WorkflowScript.run(WorkflowScript:2)", b4);
            }
        });
    }
    private static void failureInA() {
        throw new IllegalStateException("first problem");
    }
    private static void failureInB() {
        throw new IllegalStateException("second problem");
    }

    @Issue("JENKINS-26148")
    @Test public void abort() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("parallel a: {def r = semaphore 'a'; echo r}, b: {semaphore 'b'}", true));
                WorkflowRun b1 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("a/1", b1);
                SemaphoreStep.waitForStart("b/1", b1);
                b1.getExecutor().interrupt();
                story.j.assertBuildStatus(Result.ABORTED, story.j.waitForCompletion(b1));
                story.j.assertLogContains("Failed in branch a", b1);
                story.j.assertLogContains("Failed in branch b", b1);
                WorkflowRun b2 = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("a/2", b2);
                SemaphoreStep.waitForStart("b/2", b2);
                SemaphoreStep.success("a/2", "finished branch a");
                story.j.waitForMessage("finished branch a", b2);
                b2.getExecutor().interrupt();
                story.j.assertBuildStatus(Result.ABORTED, story.j.waitForCompletion(b2));
                story.j.assertLogNotContains("Failed in branch a", b2);
                story.j.assertLogContains("Failed in branch b", b2);
            }
        });
    }

    @Test
    public void localMethodCallWithinBranch() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "def touch(f) { writeFile text: '', file: f }",
                    "node {",
                    "  parallel(aa: {touch('a')}, bb: {touch('b')})",
                    "}"
                ), false));

                startBuilding().get();
                assertBuildCompletedSuccessfully();

                assertTrue(jenkins().getWorkspaceFor(p).child("a").exists());
                assertTrue(jenkins().getWorkspaceFor(p).child("b").exists());
            }
        });
    }

    @Test
    public void localMethodCallWithinBranch2() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                        "def notify(msg) {",
                        "  echo msg",
                        "}",
                        "node {",
                        "  ws {",
                        "    echo 'start'",
                        "    parallel(one: {",
                        "      notify('one')",
                        "    }, two: {",
                        "      notify('two')",
                        "    })",
                        "    echo 'end'",
                        "  }",
                        "}"
                ), false));

                startBuilding().get();
                assertBuildCompletedSuccessfully();
                story.j.assertLogContains("one", b);
                story.j.assertLogContains("two", b);
            }
        });
    }

    @Test
    public void localMethodCallWithinLotsOfBranches() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                        IOUtils.toString(getClass().getResource("localMethodCallWithinLotsOfBranches.groovy"), StandardCharsets.UTF_8), false));

                startBuilding().get();
                assertBuildCompletedSuccessfully();

                // count number of shell steps
                FlowGraphTable t = buildTable();
                int shell=0;
                for (Row r : t.getRows()) {
                    if (r.getNode() instanceof StepAtomNode) {
                        StepDescriptor descriptor = ((StepAtomNode)r.getNode()).getDescriptor();
                        if (descriptor instanceof ShellStep.DescriptorImpl || descriptor instanceof BatchScriptStep.DescriptorImpl) {
                            shell++;
                        }
                    }
                }
                assertEquals(42*3,shell);
            }
        });
    }


    /**
     * Restarts in the middle of a parallel workflow.
     */
    @Test
    public void suspend() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "node {",
                    "    parallel(",
                    "      a: { semaphore 'suspendA'; echo 'A done' },",
                    "      b: { semaphore 'suspendB'; echo 'B done' },",
                    "      c: { semaphore 'suspendC'; echo 'C done' },",
                    "    )",
                    "}"
                ), false));

                startBuilding();

                // let the workflow run until all parallel branches settle
                SemaphoreStep.waitForStart("suspendA/1", b);
                SemaphoreStep.waitForStart("suspendB/1", b);
                SemaphoreStep.waitForStart("suspendC/1", b);

                assert !e.isComplete();
                assert e.getCurrentHeads().size()==3;
                assert b.isBuilding();

                buildTable();
                shouldHaveParallelStepsInTheOrder("a","b","c");
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);

                // make sure we are still running two heads
                assert e.getCurrentHeads().size()==3;
                assert b.isBuilding();

                // we let one branch go at a time
                for (String branch : asList("A", "B")) {
                    SemaphoreStep.success("suspend" + branch + "/1", null);
                    waitForWorkflowToSuspend();

                    // until all execution joins into one, we retain all heads
                    assert e.getCurrentHeads().size() == 3;
                    assert b.isBuilding();
                }

                // when we let the last one go, it will now run till the completion
                SemaphoreStep.success("suspendC/1", null);
                story.j.waitForCompletion(b);

                // make sure all the three branches have executed to the end.
                for (String branch : asList("A", "B", "C")) {
                    story.j.assertLogContains(branch + " done", b);
                }

                // check the shape of the graph
                buildTable();
                shouldHaveSteps(SemaphoreStep.DescriptorImpl.class, 3);
                shouldHaveSteps(EchoStep.DescriptorImpl.class, 3);
                shouldHaveParallelStepsInTheOrder("a","b","c");
            }
        });
    }

    private void shouldHaveSteps(Class<? extends StepDescriptor> d, int n) {
        int count=0;
        for (Row row : t.getRows()) {
            if (row.getNode() instanceof StepAtomNode) {
                StepAtomNode a = (StepAtomNode)row.getNode();
                if (a.getDescriptor().getClass()==d)
                    count++;
            }
        }
        assertEquals(d.getName(), n, count);
    }

    /**
     * Builds {@link FlowGraphTable}. Convenient for inspecting a shape of the flow nodes.
     */
    private FlowGraphTable buildTable() {
        t = new FlowGraphTable(e);
        t.build();
        return t;
    }
    private void shouldHaveParallelStepsInTheOrder(String... expected) {
        List<String> actual = new ArrayList<>();

        for (Row row : t.getRows()) {
            ThreadNameAction a = row.getNode().getAction(ThreadNameAction.class);
            if (a!=null)
                actual.add(a.getThreadName());
        }

        assertEquals(Arrays.asList(expected),actual);
    }

    /**
     * Parallel branches become invisible once completed until the whole parallel step is completed.
     */
    @Test @Issue("JENKINS-26074")
    public void invisibleParallelBranch() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                    "    parallel(\n" +
                    "      'abc' : {\n" +
                    "        noSuchFunctionExists(); \n"+
                    "      }\n" +
                    "      ,\n" +
                    "      'waitForever' : {\n" +
                    "        semaphore 'wait'\n" +
                    "      }\n" +
                    "      ,\n" +
                    "      'someSimpleError' : {\n" +
                    "        noSuchFunctionExists(); \n"+
                    "      }\n" +
                    "    )\n"
                ), false));

                startBuilding();

                // wait for workflow to progress far enough to the point that it has finished  failing two branches
                // and pause on one
                for (int i=0; i<10; i++)
                    waitForWorkflowToSuspend();

                SemaphoreStep.waitForStart("wait/1", b);

                assertEquals("Expecting 3 heads for 3 branches", 3,e.getCurrentHeads().size());

                SemaphoreStep.success("wait/1", null);
                waitForWorkflowToComplete();

                story.j.assertBuildStatus(Result.FAILURE, b);

                // make sure the table builds OK
                buildTable();
            }
        });
    }

    @Issue("JENKINS-29413")
    @Test public void emptyMap() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition("parallel [:]", true));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
            }
        });
    }

    @Issue("JENKINS-38268")
    @Test
    public void parallelLexicalScope() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("def fn = { arg ->\n" +
                        "    arg.count = arg.count + 1;\n" +
                        "}\n" +
                        "def a = [ id: 'a', count : 0 ];\n" +
                        "def b = [ id: 'b', count : 0 ];\n" +
                        "parallel(\n" +
                        "    StepA : { fn(a); },\n" +
                        "    StepB : { fn(b); },\n" +
                        ");\n" +
                        "assert a.count == 1;\n" +
                        "assert b.count == 1;\n", true));
                story.j.buildAndAssertSuccess(p);
            }
        });
    }

    @WithoutJenkins
    @Test
    public void throwableComparator() throws Exception {
        Comparator<Throwable> comparator = new ParallelStep.ResultHandler.ThrowableComparator();
        assertEquals(
                -1,
                comparator.compare(
                        new AbortException(), new FlowInterruptedException(Result.FAILURE)));
        assertEquals(
                0,
                comparator.compare(
                        new FlowInterruptedException(Result.FAILURE),
                        new FlowInterruptedException(Result.FAILURE)));
        assertEquals(
                1,
                comparator.compare(
                        new FlowInterruptedException(Result.FAILURE), new AbortException()));
        assertEquals(
                -1,
                comparator.compare(
                        new IllegalStateException(), new FlowInterruptedException(Result.FAILURE)));
        assertEquals(
                1,
                comparator.compare(
                        new FlowInterruptedException(Result.FAILURE), new IllegalStateException()));
        assertEquals(-1, comparator.compare(new IllegalStateException(), new AbortException()));
        assertEquals(0, comparator.compare(new AbortException(), new AbortException()));
        assertEquals(1, comparator.compare(new AbortException(), new IllegalStateException()));
        assertEquals(
                1,
                comparator.compare(
                        new FlowInterruptedException(Result.SUCCESS),
                        new FlowInterruptedException(Result.FAILURE)));
        assertEquals(
                -1,
                comparator.compare(
                        new FlowInterruptedException(Result.FAILURE),
                        new FlowInterruptedException(Result.SUCCESS)));
        assertEquals(
                1,
                comparator.compare(
                        new FlowInterruptedException(Result.SUCCESS),
                        new FlowInterruptedException(Result.UNSTABLE)));
        assertEquals(
                -1,
                comparator.compare(
                        new FlowInterruptedException(Result.UNSTABLE),
                        new FlowInterruptedException(Result.SUCCESS)));
        assertEquals(
                1,
                comparator.compare(
                        new FlowInterruptedException(Result.UNSTABLE),
                        new FlowInterruptedException(Result.FAILURE)));
        assertEquals(
                -1,
                comparator.compare(
                        new FlowInterruptedException(Result.FAILURE),
                        new FlowInterruptedException(Result.UNSTABLE)));
    }

    @Issue("JENKINS-49073")
    @Test
    public void parallelPropagatesStatus() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                parallelPropagatesStatusImpl(
                        Result.SUCCESS, Result.SUCCESS, Result.SUCCESS, Result.SUCCESS);
                parallelPropagatesStatusImpl(
                        Result.UNSTABLE, Result.SUCCESS, Result.SUCCESS, Result.UNSTABLE);
                parallelPropagatesStatusImpl(
                        Result.FAILURE, Result.SUCCESS, Result.SUCCESS, Result.FAILURE);
                parallelPropagatesStatusImpl(
                        Result.FAILURE, Result.SUCCESS, Result.UNSTABLE, Result.FAILURE);
                parallelPropagatesStatusImpl(
                        Result.FAILURE, Result.SUCCESS, Result.FAILURE, Result.UNSTABLE);
                parallelPropagatesStatusImpl(
                        Result.NOT_BUILT, Result.SUCCESS, Result.NOT_BUILT);
                parallelPropagatesStatusImpl(
                        Result.NOT_BUILT, Result.SUCCESS, Result.UNSTABLE, Result.NOT_BUILT);
                parallelPropagatesStatusImpl(
                        Result.NOT_BUILT, Result.SUCCESS, Result.UNSTABLE, Result.FAILURE, Result.NOT_BUILT);
                parallelPropagatesStatusImpl(
                        Result.NOT_BUILT, Result.NOT_BUILT, Result.NOT_BUILT);
                parallelPropagatesStatusImpl(
                        Result.ABORTED, Result.SUCCESS, Result.ABORTED);
                parallelPropagatesStatusImpl(
                        Result.ABORTED, Result.SUCCESS, Result.UNSTABLE, Result.ABORTED);
                parallelPropagatesStatusImpl(
                        Result.ABORTED, Result.SUCCESS, Result.UNSTABLE, Result.FAILURE, Result.ABORTED);
                parallelPropagatesStatusImpl(
                        Result.ABORTED, Result.SUCCESS, Result.UNSTABLE, Result.FAILURE, Result.NOT_BUILT, Result.ABORTED);
                parallelPropagatesStatusImpl(
                        Result.ABORTED, Result.ABORTED, Result.ABORTED);
            }
        });
    }

    private void parallelPropagatesStatusImpl(Result upstreamResult, Result... downstreamResults)
            throws Exception {
        List<FreeStyleProject> downstreamJobs = new ArrayList<>();
        for (Result downstreamResult : downstreamResults) {
            FreeStyleProject downstreamJob = getDownstreamJob(downstreamResult);
            downstreamJobs.add(downstreamJob);
        }

        WorkflowJob upstreamJob = story.j.createProject(WorkflowJob.class);
        StringBuilder upstreamJobDefinition = new StringBuilder();
        upstreamJobDefinition.append("node {\n");
        upstreamJobDefinition.append("  parallel(\n");
        for (FreeStyleProject downstreamJob : downstreamJobs) {
            upstreamJobDefinition.append(
                    String.format("    'branch-%s': {\n", downstreamJob.getName()));
            upstreamJobDefinition.append(
                    String.format("      semaphore 'sem-%s'\n", downstreamJob.getName()));
            upstreamJobDefinition.append(
                    String.format("      build '%s'\n", downstreamJob.getName()));
            upstreamJobDefinition.append("    },\n");
        }
        upstreamJobDefinition.append("  )\n");
        upstreamJobDefinition.append("}\n");
        upstreamJob.setDefinition(new CpsFlowDefinition(upstreamJobDefinition.toString(), true));

        WorkflowRun run = upstreamJob.scheduleBuild2(0).waitForStart();
        for (int i = 0; i < downstreamJobs.size(); i++) {
            FreeStyleProject downstreamJob = downstreamJobs.get(i);
            Result downstreamResult = downstreamResults[i];

            SemaphoreStep.success(String.format("sem-%s/1", downstreamJob.getName()), null);
            story.j.waitForMessage(
                    String.format("Scheduling project: %s", downstreamJob.getName()), run);
            story.j.waitForMessage(
                    String.format("Starting building: %s", downstreamJob.getName()), run);
            List<FreeStyleBuild> downstreamBuilds = new ArrayList<>(downstreamJob.getBuilds());
            assertEquals(1, downstreamBuilds.size());
            story.j.waitForCompletion(downstreamBuilds.get(0));
            if (!downstreamResult.equals(Result.SUCCESS)) {
                story.j.waitForMessage(
                        String.format("Failed in branch branch-%s", downstreamJob.getName()), run);
            }
        }

        story.j.waitForCompletion(run);
        story.j.assertBuildStatus(upstreamResult, run);

        for (int i = 0; i < downstreamJobs.size(); i++) {
            FreeStyleProject downstreamJob = downstreamJobs.get(i);
            Result downstreamResult = downstreamResults[i];

            if (!downstreamResult.equals(Result.SUCCESS)) {
                story.j.waitForMessage(
                        String.format(
                                "%s #1 completed with status %s",
                                downstreamJob.getName(), downstreamResult.toString()),
                        run);
            }
        }
    }

    private FreeStyleProject getDownstreamJob(Result result) throws IOException {
        FreeStyleProject ds = story.j.createFreeStyleProject();
        ds.getBuildersList().add(new TestBuilder() {
            @Override
            public boolean perform(
                    AbstractBuild<?, ?> build,
                    Launcher launcher,
                    BuildListener listener)
                    throws InterruptedException, IOException {
                build.setResult(result);
                return true;
            }
        });
        ds.setQuietPeriod(0);
        return ds;
    }
}
