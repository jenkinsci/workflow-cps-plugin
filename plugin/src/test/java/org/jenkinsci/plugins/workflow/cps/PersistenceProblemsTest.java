package org.jenkinsci.plugins.workflow.cps;

import com.google.common.util.concurrent.ListenableFuture;
import hudson.model.Queue;
import hudson.model.Result;
import org.apache.commons.io.FileUtils;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.DurabilityHintJobProperty;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.nio.file.Files;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Verifies we can cope with all the bizarre quirks that occur when persistence fails or something unexpected happens.
 */
public class PersistenceProblemsTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    /** Execution bombed out due to some sort of irrecoverable persistence issue. */
    static void assertNulledExecution(WorkflowRun run) throws Exception {
        if (run.isBuilding()) {
            System.out.println("Run initially building, going to wait a second to see if it finishes, run="+run);
            Thread.sleep(1000);
        }
        Assert.assertFalse(run.isBuilding());
        Assert.assertNotNull(run.getResult());
        FlowExecution fe = run.getExecution();
        Assert.assertNull(fe);
    }

    /** Verifies all the assumptions about a cleanly finished build. */
    static void assertCompletedCleanly(WorkflowRun run) throws Exception {
        while (run.isBuilding()) {
            Thread.sleep(100); // TODO seems to be unpredictable
        }
        Assert.assertNotNull(run.getResult());
        FlowExecution fe = run.getExecution();
        FlowExecutionList.get().forEach(f -> {
            if (fe != null && f == fe) {
                Assert.fail("FlowExecution still in FlowExecutionList!");
            }
        });
        Assert.assertTrue("Queue not empty after completion!", Queue.getInstance().isEmpty());

        if (fe instanceof CpsFlowExecution) {
            CpsFlowExecution cpsExec = (CpsFlowExecution)fe;
            Assert.assertTrue(cpsExec.isComplete());
            Assert.assertEquals(Boolean.TRUE, cpsExec.done);
            Assert.assertEquals(1, cpsExec.getCurrentHeads().size());
            Assert.assertTrue(cpsExec.isComplete());
            Assert.assertTrue(cpsExec.getCurrentHeads().get(0) instanceof FlowEndNode);
            Assert.assertTrue(cpsExec.startNodes == null || cpsExec.startNodes.isEmpty());
            while (cpsExec.blocksRestart()) {
                Thread.sleep(100); // TODO ditto
            }
        } else {
            System.out.println("WARNING: no FlowExecutionForBuild");
        }
    }

    static void assertCleanInProgress(WorkflowRun run) throws Exception {
        Assert.assertTrue(run.isBuilding());
        Assert.assertNull(run.getResult());
        FlowExecution fe = run.getExecution();
        AtomicBoolean hasExecutionInList = new AtomicBoolean(false);
        FlowExecutionList.get().forEach(f -> {
            if (fe != null && f == fe) {
                hasExecutionInList.set(true);
            }
        });
        if (!hasExecutionInList.get()) {
            Assert.fail("Build completed but should still show in FlowExecutionList");
        }
        CpsFlowExecution cpsExec = (CpsFlowExecution)fe;
        Assert.assertFalse(cpsExec.isComplete());
        Assert.assertEquals(Boolean.FALSE, cpsExec.done);
        Assert.assertFalse(cpsExec.getCurrentHeads().get(0) instanceof FlowEndNode);
        Assert.assertTrue(cpsExec.startNodes != null && !cpsExec.startNodes.isEmpty());
    }

    static void assertResultMatchExecutionAndRun(WorkflowRun run, Result[] executionAndBuildResult) throws Exception {
        Assert.assertEquals(executionAndBuildResult[0], ((CpsFlowExecution)(run.getExecution())).getResult());
        Assert.assertEquals(executionAndBuildResult[1], run.getResult());
    }

    /** Create and run a basic build before we mangle its persisted contents.  Stores job number to jobIdNumber index 0. */
    private static WorkflowRun runBasicBuild(JenkinsRule j, String jobName, int[] jobIdNumber, FlowDurabilityHint hint) throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, jobName);
        job.setDefinition(new CpsFlowDefinition("echo 'doSomething'", true));
        job.addProperty(new DurabilityHintJobProperty(hint));
        WorkflowRun run = j.buildAndAssertSuccess(job);
        jobIdNumber[0] = run.getNumber();
        assertCompletedCleanly(run);
        return run;
    }

    /** Create and run a basic build before we mangle its persisted contents.  Stores job number to jobIdNumber index 0. */
    private static WorkflowRun runBasicBuild(JenkinsRule j, String jobName, int[] jobIdNumber) throws Exception {
        return runBasicBuild(j, jobName, jobIdNumber, FlowDurabilityHint.MAX_SURVIVABILITY);
    }

    /** Sets up a running build that is waiting on input. */
    private static WorkflowRun runBasicPauseOnInput(JenkinsRule j, String jobName, int[] jobIdNumber, FlowDurabilityHint durabilityHint) throws Exception {
        WorkflowJob job = j.jenkins.createProject(WorkflowJob.class, jobName);
        job.setDefinition(new CpsFlowDefinition("input 'pause'", true));
        job.addProperty(new DurabilityHintJobProperty(durabilityHint));

        WorkflowRun run = job.scheduleBuild2(0).getStartCondition().get();
        ListenableFuture<FlowExecution> listener = run.getExecutionPromise();
        FlowExecution exec = listener.get();
        while(exec.getCurrentHeads().isEmpty() || (exec.getCurrentHeads().get(0) instanceof FlowStartNode)) {  // Wait until input step starts
            System.out.println("Waiting for input step to begin");
            Thread.sleep(50);
        }
        while(run.getAction(InputAction.class) == null) {  // Wait until input step starts
            System.out.println("Waiting for input action to get attached to run");
            Thread.sleep(50);
        }
        Thread.sleep(100L);  // A little extra buffer for persistence etc
        jobIdNumber[0] = run.getNumber();
        return run;
    }

    private static WorkflowRun runBasicPauseOnInput(JenkinsRule j, String jobName, int[] jobIdNumber) throws Exception {
        return runBasicPauseOnInput(j, jobName, jobIdNumber, FlowDurabilityHint.MAX_SURVIVABILITY);
    }

    private static InputStepExecution getInputStepExecution(WorkflowRun run, String inputMessage) throws Exception {
        InputAction ia = run.getAction(InputAction.class);
        List<InputStepExecution> execList = ia.getExecutions();
        return execList.stream().filter(e -> inputMessage.equals(e.getInput().getMessage())).findFirst().orElse(null);
    }

    final  static String DEFAULT_JOBNAME = "testJob";

    /** Simulates something happening badly during final shutdown, which may cause build to not appear done. */
    @Test
    public void completedFinalFlowNodeNotPersisted() throws Exception {
        final int[] build = new int[1];
        final Result[] executionAndBuildResult = new Result[2];
        story.thenWithHardShutdown( j -> {
            WorkflowRun run = runBasicBuild(j, DEFAULT_JOBNAME, build);
            String finalId = run.getExecution().getCurrentHeads().get(0).getId();

            // Hack but deletes the file from disk
            CpsFlowExecution cpsExec = (CpsFlowExecution)(run.getExecution());
            Files.delete(cpsExec.getStorageDir().toPath().resolve(finalId+".xml"));
            executionAndBuildResult[0] = ((CpsFlowExecution)(run.getExecution())).getResult();
            executionAndBuildResult[1] = run.getResult();
        });
        story.then(j-> {
            WorkflowJob r = j.jenkins.getItemByFullName(DEFAULT_JOBNAME, WorkflowJob.class);
            WorkflowRun run = r.getBuildByNumber(build[0]);
            assertCompletedCleanly(run);
            //            assertNulledExecution(run);
            Assert.assertEquals(Result.SUCCESS, run.getResult());
            assertResultMatchExecutionAndRun(run, executionAndBuildResult);
        });
    }
    /** Perhaps there was a serialization error breaking the FlowGraph persistence for non-durable mode. */
    @Test
    public void completedNoNodesPersisted() throws Exception {
        final int[] build = new int[1];
        final Result[] executionAndBuildResult = new Result[2];
        story.thenWithHardShutdown( j -> {
            WorkflowRun run = runBasicBuild(j, DEFAULT_JOBNAME, build);
            FileUtils.deleteDirectory(((CpsFlowExecution)(run.getExecution())).getStorageDir());
            executionAndBuildResult[0] = ((CpsFlowExecution)(run.getExecution())).getResult();
            executionAndBuildResult[1] = run.getResult();
        });
        story.then(j-> {
            WorkflowJob r = j.jenkins.getItemByFullName(DEFAULT_JOBNAME, WorkflowJob.class);
            WorkflowRun run = r.getBuildByNumber(build[0]);
            assertCompletedCleanly(run);
            // assertNulledExecution(run);
            Assert.assertEquals(Result.SUCCESS, run.getResult());
            assertResultMatchExecutionAndRun(run, executionAndBuildResult);
        });
    }

    /** Simulates case where done flag was not persisted. */
    @Test
    public void completedButWrongDoneStatus() throws Exception {
        final int[] build = new int[1];
        final Result[] executionAndBuildResult = new Result[2];
        story.thenWithHardShutdown( j -> {
            WorkflowRun run = runBasicBuild(j, DEFAULT_JOBNAME, build);
            String finalId = run.getExecution().getCurrentHeads().get(0).getId();

            // Hack but deletes the FlowNodeStorage from disk
            CpsFlowExecution cpsExec = (CpsFlowExecution)(run.getExecution());
            cpsExec.done = false;
            cpsExec.saveOwner();
            executionAndBuildResult[0] = ((CpsFlowExecution)(run.getExecution())).getResult();
            executionAndBuildResult[1] = run.getResult();
        });
        story.then(j-> {
            WorkflowJob r = j.jenkins.getItemByFullName(DEFAULT_JOBNAME, WorkflowJob.class);
            WorkflowRun run = r.getBuildByNumber(build[0]);
            assertCompletedCleanly(run);
            Assert.assertEquals(Result.SUCCESS, run.getResult());
            assertResultMatchExecutionAndRun(run, executionAndBuildResult);
        });
    }

    @Test
    public void inProgressNormal() throws Exception {
        final int[] build = new int[1];
        story.then( j -> {
            WorkflowRun run = runBasicPauseOnInput(j, DEFAULT_JOBNAME, build);
        });
        story.then( j->{
            WorkflowJob r = j.jenkins.getItemByFullName(DEFAULT_JOBNAME, WorkflowJob.class);
            WorkflowRun run = r.getBuildByNumber(build[0]);
            assertCleanInProgress(run);
            InputStepExecution exec = getInputStepExecution(run, "pause");
            exec.doProceedEmpty();
            j.waitForCompletion(run);
            assertCompletedCleanly(run);
            Assert.assertEquals(Result.SUCCESS, run.getResult());
        });
    }

    @Test
    @Ignore
    public void inProgressMaxPerfCleanShutdown() throws Exception {
        final int[] build = new int[1];
        story.then( j -> {
            WorkflowRun run = runBasicPauseOnInput(j, DEFAULT_JOBNAME, build, FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
            // SHOULD still save at end via persist-at-shutdown hooks
        });
        story.then( j->{
            WorkflowJob r = j.jenkins.getItemByFullName(DEFAULT_JOBNAME, WorkflowJob.class);
            WorkflowRun run = r.getBuildByNumber(build[0]);
            assertCleanInProgress(run);
            InputStepExecution exec = getInputStepExecution(run, "pause");
            exec.doProceedEmpty();
            j.waitForCompletion(run);
            assertCompletedCleanly(run);
            Assert.assertEquals(Result.SUCCESS, run.getResult());
        });
    }

    @Test
    @Ignore
    public void inProgressMaxPerfDirtyShutdown() throws Exception {
        final int[] build = new int[1];
        final String[] finalNodeId = new String[1];
        story.thenWithHardShutdown( j -> {
            runBasicPauseOnInput(j, DEFAULT_JOBNAME, build, FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
            // SHOULD still save at end via persist-at-shutdown hooks
        });
        story.then( j->{
            WorkflowJob r = j.jenkins.getItemByFullName(DEFAULT_JOBNAME, WorkflowJob.class);
            WorkflowRun run = r.getBuildByNumber(build[0]);
            Thread.sleep(1000);
            j.waitForCompletion(run);
            assertCompletedCleanly(run);
            Assert.assertEquals(Result.FAILURE, run.getResult());
            finalNodeId[0] = run.getExecution().getCurrentHeads().get(0).getId();
        });
        story.then(j-> {
            WorkflowJob r = j.jenkins.getItemByFullName(DEFAULT_JOBNAME, WorkflowJob.class);
            WorkflowRun run = r.getBuildByNumber(build[0]);
            assertCompletedCleanly(run);
            Assert.assertEquals(finalNodeId[0], run.getExecution().getCurrentHeads().get(0).getId());
            // JENKINS-50199, verify it doesn't try to resume again
        });
    }

    @Test
    public void inProgressButFlowNodesLost() throws Exception {
        final int[] build = new int[1];
        story.thenWithHardShutdown( j -> {
            WorkflowRun run = runBasicPauseOnInput(j, DEFAULT_JOBNAME, build);
            CpsFlowExecution cpsExec = (CpsFlowExecution)(run.getExecution());
            FileUtils.deleteDirectory(((CpsFlowExecution)(run.getExecution())).getStorageDir());
        });
        story.then( j->{
            WorkflowJob r = j.jenkins.getItemByFullName(DEFAULT_JOBNAME, WorkflowJob.class);
            WorkflowRun run = r.getBuildByNumber(build[0]);
            assertCompletedCleanly(run);
        });
    }

    @Test
    /** Build okay but program fails to load */
    public void inProgressButProgramLoadFailure() throws Exception {
        final int[] build = new int[1];
        story.thenWithHardShutdown( j -> {
            WorkflowRun run = runBasicPauseOnInput(j, DEFAULT_JOBNAME, build);
            CpsFlowExecution cpsExec = (CpsFlowExecution)(run.getExecution());
            // Wait until program.dat is written and then delete it.
            while (!Files.exists(cpsExec.getProgramDataFile().toPath())) {
                Thread.sleep(100);
            }
            Files.delete(cpsExec.getProgramDataFile().toPath());
        });
        story.then( j->{
            WorkflowJob r = j.jenkins.getItemByFullName(DEFAULT_JOBNAME, WorkflowJob.class);
            WorkflowRun run = r.getBuildByNumber(build[0]);
            assertCompletedCleanly(run);
        });
    }

    @Test
    /** Build okay but then the start nodes get screwed up */
    public void inProgressButStartBlocksLost() throws Exception {
        final int[] build = new int[1];
        story.thenWithHardShutdown( j -> {
            WorkflowRun run = runBasicPauseOnInput(j, DEFAULT_JOBNAME, build);
            CpsFlowExecution cpsExec = (CpsFlowExecution)(run.getExecution());
            cpsExec.startNodes.push(new FlowStartNode(cpsExec, cpsExec.iotaStr()));
            run.save();
        });
        story.then( j->{
            WorkflowJob r = j.jenkins.getItemByFullName(DEFAULT_JOBNAME, WorkflowJob.class);
            WorkflowRun run = r.getBuildByNumber(build[0]);
            assertCompletedCleanly(run);
        });
    }

    @Issue("JENKINS-50888")  // Tried to modify build without lazy load being triggered
    @Test public void modifyBeforeLazyLoad() {
        story.then(r -> {  // Normal build
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("echo 'dosomething'", true));
            r.buildAndAssertSuccess(p);
        });
        story.then(r -> {  // But wait, we try to modify the build without loading the execution
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getBuildByNumber(1);
            b.setDescription("Bob");
            b.save();  // Before the JENKINS-50888 fix this would trigger an IOException
        });
        story.then( r-> {  // Verify that the FlowExecutionOwner can trigger lazy-load correctly
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getBuildByNumber(1);
            Assert.assertEquals("Bob", b.getDescription());
            Assert.assertEquals("4", b.getExecution().getCurrentHeads().get(0).getId());
        });
    }
}
