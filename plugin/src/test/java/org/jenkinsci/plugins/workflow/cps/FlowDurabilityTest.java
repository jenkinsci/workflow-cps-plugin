package org.jenkinsci.plugins.workflow.cps;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Item;
import hudson.model.Result;
import hudson.util.CopyOnWriteList;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.TestDurabilityHintProvider;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.actions.LogAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowScanningUtils;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepNamePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.job.properties.DurabilityHintJobProperty;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.support.storage.FlowNodeStorage;
import org.jenkinsci.plugins.workflow.support.storage.BulkFlowNodeStorage;
import org.jenkinsci.plugins.workflow.support.storage.SimpleXStreamFlowNodeStorage;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Tests implementations designed to verify handling of the flow durability levels and persistence of pipeline state.
 *
 * <strong>This means:</strong>
 * <ol>
 *     <li>Persisting FlowNode data in an appropriately consistent fashion.</li>
 *     <li>Persisting Program data for running pipelines as appropriate.</li>
 *     <li>Not leaving orphaned durable tasks.</li>
 *     <li>Being able to load runs & FlowExecutions as appropriate, with up-to-date info.</li>
 *     <li>Being able to resume execution when we are supposed to.</li>
 * </ol>
 */
public class FlowDurabilityTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Rule
    public TimedRepeatRule repeater = new TimedRepeatRule();

    // Used in Race-condition/persistence fuzzing where we need to run repeatedly
    static class TimedRepeatRule implements TestRule {

        @Target({ElementType.METHOD})
        @Retention(RetentionPolicy.RUNTIME)
        @interface RepeatForTime {
            long repeatMillis();
        }

        private static class RepeatedStatement extends Statement {
            private final Statement repeatedStmt;
            private final long repeatMillis;

            private RepeatedStatement(Statement stmt, long millis) {
                this.repeatedStmt = stmt;
                this.repeatMillis = millis;
            }

            @Override
            public void evaluate() throws Throwable {
                long start = System.currentTimeMillis();
                while(Math.abs(System.currentTimeMillis()-start)<repeatMillis) {
                    repeatedStmt.evaluate();
                }
            }
        }

        @Override
        public Statement apply(Statement statement, Description description) {
            RepeatForTime rep = description.getAnnotation(RepeatForTime.class);
            if (rep == null) {
                return statement;
            } else {
                return new RepeatedStatement(statement, rep.repeatMillis());
            }
        }
    }

    static WorkflowRun createAndRunBasicJob(Jenkins jenkins, String jobName, FlowDurabilityHint durabilityHint) throws Exception {
        return createAndRunBasicJob(jenkins, jobName, durabilityHint, 1);
    }

    static void assertBaseStorageType(FlowExecution exec, Class<? extends FlowNodeStorage> storageClass) throws Exception {
        if (exec instanceof CpsFlowExecution) {
            FlowNodeStorage store = ((CpsFlowExecution) exec).getStorage();
            if (store instanceof CpsFlowExecution.TimingFlowNodeStorage) {
                Field f = CpsFlowExecution.TimingFlowNodeStorage.class.getDeclaredField("delegate");
                f.setAccessible(true);
                FlowNodeStorage delegateStore = (FlowNodeStorage)(f.get(store));
                Assert.assertEquals(storageClass.toString(), delegateStore.getClass().toString());
            }
        }
    }

    /** Verify we didn't lose TimingAction */
    static void assertHasTimingAction(FlowExecution exec) throws Exception {
        DepthFirstScanner scan = new DepthFirstScanner();
        for (FlowNode node : scan.allNodes(exec)) {
            try {
                if (!(node instanceof FlowStartNode) && !(node instanceof FlowEndNode)) {
                    Assert.assertNotNull("Missing TimingAction on node", node.getPersistentAction(TimingAction.class));
                }
            } catch (Exception ex) {
                throw new Exception("Error with node: "+node.getId(), ex);
            }
        }
    }

    /** Create and run a job with a semaphore and basic steps -- takes a semaphoreIndex in case you have multiple semaphores of the same name in one test.*/
    static WorkflowRun createAndRunBasicJob(Jenkins jenkins, String jobName, FlowDurabilityHint durabilityHint, int semaphoreIndex) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, jobName);
        CpsFlowDefinition def = new CpsFlowDefinition("node {\n " +
                "semaphore 'halt' \n" +
                "} \n" +
                "echo 'I like cheese'\n", false);
        TestDurabilityHintProvider provider = Jenkins.get().getExtensionList(TestDurabilityHintProvider.class).get(0);
        provider.registerHint(jobName, durabilityHint);
        job.setDefinition(def);
        WorkflowRun run = job.scheduleBuild2(0).getStartCondition().get();
        SemaphoreStep.waitForStart("halt/"+semaphoreIndex, run);
        Assert.assertEquals(durabilityHint, run.getExecution().getDurabilityHint());
        Assert.assertFalse(run.getExecution().isComplete());
        Assert.assertFalse(((CpsFlowExecution)(run.getExecution())).done);
        if (durabilityHint.isPersistWithEveryStep()) {
            assertBaseStorageType(run.getExecution(), SimpleXStreamFlowNodeStorage.class);
        } else {
            assertBaseStorageType(run.getExecution(), BulkFlowNodeStorage.class);
        }
        Assert.assertEquals("semaphore", run.getExecution().getCurrentHeads().get(0).getDisplayFunctionName());
        return run;
    }

    static WorkflowRun createAndRunSleeperJob(Jenkins jenkins, String jobName, FlowDurabilityHint durabilityHint) throws Exception {
        Item prev = jenkins.getItemByFullName(jobName);
        if (prev != null) {
            prev.delete();
        }

        WorkflowJob job = jenkins.createProject(WorkflowJob.class, jobName);
        CpsFlowDefinition def = new CpsFlowDefinition("node {\n " +
                "sleep 30 \n" +
                "} \n" +
                "echo 'I like cheese'\n", false);
        TestDurabilityHintProvider provider = Jenkins.get().getExtensionList(TestDurabilityHintProvider.class).get(0);
        provider.registerHint(jobName, durabilityHint);
        job.setDefinition(def);
        WorkflowRun run = job.scheduleBuild2(0).getStartCondition().get();
        Thread.sleep(4000L);  // Hacky but we just need to ensure this can start up
        Assert.assertFalse(run.getExecution().isComplete());
        Assert.assertFalse(((CpsFlowExecution)(run.getExecution())).done);
        Assert.assertEquals(durabilityHint, run.getExecution().getDurabilityHint());
        Assert.assertEquals("sleep", run.getExecution().getCurrentHeads().get(0).getDisplayFunctionName());
        return run;
    }

    private static void verifyExecutionRemoved(WorkflowRun run) throws Exception{
        // Verify we've removed all FlowExcecutionList entries
        FlowExecutionList list = FlowExecutionList.get();
        for (FlowExecution fe : list) {
            if (fe == run.getExecution()) {
                Assert.fail("Run still has an execution in the list and should be removed!");
            }
        }
        Field f = list.getClass().getDeclaredField("runningTasks");
        f.setAccessible(true);
        CopyOnWriteList<FlowExecutionOwner> runningTasks = (CopyOnWriteList<FlowExecutionOwner>)(f.get(list));
        Assert.assertFalse(runningTasks.contains(run.asFlowExecutionOwner()));
    }

    static void verifySucceededCleanly(Jenkins j, WorkflowRun run) throws Exception {
        Assert.assertEquals(Result.SUCCESS, run.getResult());
        int outputHash = run.getLog().hashCode();
        FlowExecution exec = run.getExecution();
        verifyCompletedCleanly(j, run);

        // Confirm the flow graph is fully navigable and contains the heads with appropriate ending
        DepthFirstScanner scan = new DepthFirstScanner();
        List<FlowNode> allNodes = scan.allNodes(exec);
        FlowNode endNode = exec.getCurrentHeads().get(0);
        Assert.assertEquals(FlowEndNode.class, endNode.getClass());
        assert allNodes.contains(endNode);
        Assert.assertEquals(8, allNodes.size());

        // Graph structure assertions
        Assert.assertEquals(2, scan.filteredNodes(endNode, (Predicate)(Predicates.instanceOf(StepStartNode.class))).size());
        Assert.assertEquals(2, scan.filteredNodes(endNode, (Predicate)(Predicates.instanceOf(StepEndNode.class))).size());
        Assert.assertEquals(1, scan.filteredNodes(endNode, (Predicate)(Predicates.instanceOf(FlowStartNode.class))).size());

        Predicate<FlowNode> sleepOrSemaphoreMatch = Predicates.or(
                new NodeStepNamePredicate(StepDescriptor.byFunctionName("semaphore").getId()),
                new NodeStepNamePredicate(StepDescriptor.byFunctionName("sleep").getId())
        );
        Assert.assertEquals(1, scan.filteredNodes(endNode, sleepOrSemaphoreMatch).size());
        Assert.assertEquals(1, scan.filteredNodes(endNode, new NodeStepNamePredicate(StepDescriptor.byFunctionName("echo").getId())).size());

        for (FlowNode node : (List<FlowNode>)(scan.filteredNodes(endNode, (Predicate)(Predicates.instanceOf(StepNode.class))))) {
            Assert.assertNotNull("Node: "+node.toString()+" does not have a TimingAction", node.getAction(TimingAction.class));
        }
        assertHasTimingAction(run.getExecution());
    }

    /** If it's a {@link SemaphoreStep} we test less rigorously because that blocks async GraphListeners. */
    static void verifySafelyResumed(JenkinsRule rule, WorkflowRun run, boolean isSemaphore, String logStart) throws Exception {
        assert run.isBuilding();
        FlowExecution exec = run.getExecution();

        // Assert that we have the appropriate flow graph entries
        List<FlowNode> heads = exec.getCurrentHeads();
        Assert.assertEquals(1, heads.size());
        FlowNode node = heads.get(0);
        String name = node.getDisplayFunctionName();
        Assert.assertTrue("Head node not a semaphore step or sleep: "+name, "semaphore".equals(name) || "sleep".equals(name));
        if (!isSemaphore) {
            Assert.assertNotNull(node.getPersistentAction(TimingAction.class));
            Assert.assertNotNull(node.getPersistentAction(ArgumentsAction.class));
            Assert.assertNotNull(node.getAction(LogAction.class));
        } else {
            SemaphoreStep.success("halt/1", Result.SUCCESS);
        }
        assertHasTimingAction(run.getExecution());
        rule.waitForCompletion(run);
        verifySucceededCleanly(rule.jenkins, run);
        rule.assertLogContains(logStart, run);
    }

    /** Waits until the build to resume or die. */
    static void waitForBuildToResumeOrFail(WorkflowRun run) throws Exception {
        CpsFlowExecution execution = (CpsFlowExecution)(run.getExecution());
        long nanoStartTime = System.nanoTime();
        while (true) {
            if (!run.isBuilding()) {
                return;
            }
            long currentTime = System.nanoTime();
            if (TimeUnit.SECONDS.convert(currentTime-nanoStartTime, TimeUnit.NANOSECONDS) > 10) {
                StringBuilder builder = new StringBuilder();
                builder.append("Run result: "+run.getResult());
                builder.append(" and execution != null:"+run.getExecution() != null+" ");
                FlowExecution exec = run.getExecution();
                if (exec instanceof CpsFlowExecution) {
                    CpsFlowExecution cpsFlow = (CpsFlowExecution)exec;
                    builder.append(", FlowExecution is paused: "+cpsFlow.isPaused())
                            .append(", FlowExecution is complete: "+cpsFlow.isComplete())
                            .append(", FlowExecution result: "+cpsFlow.getResult())
                            .append(", FlowExecution PersistedClean: "+cpsFlow.persistedClean).append('\n');
                }
                throw new TimeoutException("Build didn't resume or fail in a timely fashion. "+builder.toString());
            }
            Thread.sleep(100L);
        }
    }

    static void verifyFailedCleanly(Jenkins j, WorkflowRun run) throws Exception {

        if (run.isBuilding()) {  // Give the run a little bit of time to see if it can resume or not
            FlowExecution exec = run.getExecution();
            if (exec instanceof CpsFlowExecution) {
                waitForBuildToResumeOrFail(run);
            } else {
                Thread.sleep(4000L);
            }
        }

        if (run.getExecution() instanceof CpsFlowExecution) {
            CpsFlowExecution cfe = (CpsFlowExecution)(run.getExecution());
            assert cfe.isComplete() || (cfe.programPromise != null && cfe.programPromise.isDone());
        }

        assert !run.isBuilding();

        if (run.getExecution() instanceof  CpsFlowExecution) {
            Assert.assertEquals(Result.FAILURE, ((CpsFlowExecution) run.getExecution()).getResult());
        }

        Assert.assertEquals(Result.FAILURE, run.getResult());
        assert !run.isBuilding();
        // TODO verify all blocks cleanly closed out, so Block start and end nodes have same counts and FlowEndNode is last node
        verifyCompletedCleanly(j, run);
    }

    /** Verifies all the universal post-build cleanup was done, regardless of pass/fail state. */
    static void verifyCompletedCleanly(Jenkins j, WorkflowRun run) throws Exception {
        // Assert that we have the appropriate flow graph entries
        FlowExecution exec = run.getExecution();
        List<FlowNode> heads = exec.getCurrentHeads();
        Assert.assertEquals(1, heads.size());
        verifyNoTasksRunning(j);
        Assert.assertEquals(0, exec.getCurrentExecutions(false).get().size());

        if (exec instanceof CpsFlowExecution) {
            CpsFlowExecution cpsFlow = (CpsFlowExecution)exec;
            assert cpsFlow.getStorage() != null;
            Assert.assertFalse("Should always be able to retrieve script", StringUtils.isEmpty(cpsFlow.getScript()));
            Assert.assertNull("We should have no Groovy shell left or that's a memory leak", cpsFlow.getShell());
            Assert.assertNull("We should have no Groovy shell left or that's a memory leak", cpsFlow.getTrustedShell());
            Assert.assertTrue(cpsFlow.done);
            assert cpsFlow.isComplete();
            assert cpsFlow.heads.size() == 1;
            Map.Entry<Integer, FlowHead> finalHead = cpsFlow.heads.entrySet().iterator().next();
            assert finalHead.getValue().get() instanceof FlowEndNode;
            Assert.assertEquals(cpsFlow.storage.getNode(finalHead.getValue().get().getId()), finalHead.getValue().get());
        }

        verifyExecutionRemoved(run);
    }

    private static void assertNoTasksRunning(Jenkins j) {
        j.getQueue().maintain();
        assert j.getQueue().isEmpty();
        Computer[] computerList = j.getComputers();
        for (Computer c : computerList) {
            List<Executor> executors = c.getExecutors();
            for (Executor ex : executors) {
                if (ex.isBusy()) {
                    Assert.fail("Computer "+c+" has an Executor "+ex+" still running a task: "+ex.getCurrentWorkUnit());
                }
            }
        }
    }

    /** Verifies we have nothing left that uses an executor for a given job. */
    static void verifyNoTasksRunning(Jenkins j) throws Exception {
        try {
            assertNoTasksRunning(j);
        } catch (AssertionError ae) {
            // Allows for slightly delayed processes
            Thread.sleep(1000L);
            assertNoTasksRunning(j);
        }
    }

    /**
     * Confirm that for ALL implementations, a run can complete and be loaded if you restart after completion.
     */
    @Test
    public void testCompleteAndLoadBuilds() throws Exception {
        final FlowDurabilityHint[] durabilityHints = FlowDurabilityHint.values();
        final WorkflowJob[] jobs = new WorkflowJob[durabilityHints.length];
        final String[] logOutput = new String[durabilityHints.length];

        // Create and run jobs for each of the durability hints
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                int i = 1;
                for (FlowDurabilityHint hint : durabilityHints) {
                    try{
                        WorkflowRun run = createAndRunBasicJob(story.j.jenkins, "basicJob-"+hint.toString(), hint, i);
                        jobs[i-1] = run.getParent();
                        SemaphoreStep.success("halt/"+i++,Result.SUCCESS);
                        story.j.waitForCompletion(run);
                        story.j.assertBuildStatus(Result.SUCCESS, run);
                        logOutput[i-2] = JenkinsRule.getLog(run);
                        assertHasTimingAction(run.getExecution());
                    } catch (AssertionError ae) {
                        System.out.println("Error with durability level: "+hint);
                        throw ae;
                    }
                }
            }
        });

        //Restart and confirm we can still load them.
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                for (int i=0; i<durabilityHints.length; i++) {
                    WorkflowJob j  = jobs[i];
                    try{
                        WorkflowRun run = j.getLastBuild();
                        verifySucceededCleanly(story.j.jenkins, run);
                        Assert.assertEquals(durabilityHints[i], run.getExecution().getDurabilityHint());
                        Assert.assertEquals(logOutput[i], JenkinsRule.getLog(run));
                        assertHasTimingAction(run.getExecution());
                    } catch (AssertionError ae) {
                        System.out.println("Error with durability level: "+durabilityHints[i]);
                        throw ae;
                    }
                }
            }
        });
    }

    /**
     * Verifies that if we're only durable against clean restarts, the pipeline will survive it.
     */
    @Test
    public void testDurableAgainstCleanRestartSurvivesIt() throws Exception {
        final String jobName = "durableAgainstClean";
        final String[] logStart = new String[1];

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Jenkins jenkins = story.j.jenkins;
                WorkflowRun run = createAndRunSleeperJob(story.j.jenkins, jobName, FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
                FlowExecution exec = run.getExecution();
                assertBaseStorageType(exec, BulkFlowNodeStorage.class);
                logStart[0] = JenkinsRule.getLog(run);
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = story.j.jenkins.getItemByFullName(jobName, WorkflowJob.class).getLastBuild();
                Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, run.getExecution().getDurabilityHint());
                assertBaseStorageType(run.getExecution(), BulkFlowNodeStorage.class);
                verifySafelyResumed(story.j, run, true, logStart[0]);
            }
        });
    }

    /**
     * Verifies that paused pipelines survive dirty restarts
     */
    @Test
    public void testPauseForcesLowDurabilityToPersist() throws Exception {
        final String jobName = "durableAgainstClean";
        final String[] logStart = new String[1];

        story.addStepWithDirtyShutdown(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Jenkins jenkins = story.j.jenkins;
                WorkflowRun run = createAndRunSleeperJob(story.j.jenkins, jobName, FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
                FlowExecution exec = run.getExecution();
                assertBaseStorageType(exec, BulkFlowNodeStorage.class);
                logStart[0] = JenkinsRule.getLog(run);
                if (run.getExecution() instanceof CpsFlowExecution) {
                    CpsFlowExecution cpsFlow = (CpsFlowExecution)(run.getExecution());
                    cpsFlow.pause(true);
                    long timeout = System.nanoTime()+TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS);
                    while(System.nanoTime() < timeout && !cpsFlow.isPaused()) {
                        Thread.sleep(100L);
                    }
                }
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = story.j.jenkins.getItemByFullName(jobName, WorkflowJob.class).getLastBuild();
                if (run.getExecution() instanceof CpsFlowExecution) {
                    CpsFlowExecution cpsFlow = (CpsFlowExecution)(run.getExecution());
                    cpsFlow.pause(false);
                    long timeout = System.nanoTime()+TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS);
                    while(System.nanoTime() < timeout && cpsFlow.isPaused()) {
                        Thread.sleep(100L);
                    }
                }
                Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, run.getExecution().getDurabilityHint());
                assertBaseStorageType(run.getExecution(), BulkFlowNodeStorage.class);
                verifySafelyResumed(story.j, run, false, logStart[0]);
            }
        });
    }

    /** Verify that our flag for whether or not a build was cleanly persisted gets reset when things happen.
     */
    @Test
    public void testDurableAgainstCleanRestartResetsCleanlyPersistedFlag() throws Exception {
        final String jobName = "durableAgainstClean";
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob job = story.j.jenkins.createProject(WorkflowJob.class, jobName);
                CpsFlowDefinition def = new CpsFlowDefinition("node {\n " +
                        "  sleep 30 \n" +
                        "  dir('nothing'){sleep 30;}\n"+
                        "} \n" +
                        "echo 'I like chese'\n", false);
                TestDurabilityHintProvider provider = Jenkins.get().getExtensionList(TestDurabilityHintProvider.class).get(0);
                provider.registerHint(jobName, FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
                job.setDefinition(def);
                WorkflowRun run = job.scheduleBuild2(0).getStartCondition().get();
                Thread.sleep(2000L);  // Hacky but we just need to ensure this can start up
            }
        });
        story.addStepWithDirtyShutdown(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = story.j.jenkins.getItemByFullName(jobName, WorkflowJob.class).getLastBuild();
                assert run.isBuilding();
                assert run.getResult() != Result.FAILURE;
                Thread.sleep(35000);  // Step completes
                if (run.getExecution() instanceof  CpsFlowExecution) {
                    CpsFlowExecution exec = (CpsFlowExecution)run.getExecution();
                    assert exec.persistedClean == null;
                }
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Thread.sleep(2000L);  // Just to allow time for basic async processes to finish.
               verifyFailedCleanly(story.j.jenkins, story.j.jenkins.getItemByFullName(jobName, WorkflowJob.class).getLastBuild());
            }
        });
    }


    /** Verify that if the master dies messily and we're not durable against that, build fails cleanly.
     */
    @Test
    public void testDurableAgainstCleanRestartFailsWithDirtyShutdown() throws Exception {
        final String[] logStart = new String[1];
        story.addStepWithDirtyShutdown(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = createAndRunSleeperJob(story.j.jenkins, "durableAgainstClean", FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
                Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, run.getExecution().getDurabilityHint());
                logStart[0] = JenkinsRule.getLog(run);
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = story.j.jenkins.getItemByFullName("durableAgainstClean", WorkflowJob.class).getLastBuild();
                verifyFailedCleanly(story.j.jenkins, run);
                story.j.assertLogContains(logStart[0], run);
            }
        });
    }

    /** Verify that if the master dies messily and FlowNode storage is lost entirely we fail the build cleanly.
     */
    @Test
    @Issue("JENKINS-48824")
    public void testDurableAgainstCleanRestartFailsWithBogusStorageFile() throws Exception {
        final String[] logStart = new String[1];
        story.addStepWithDirtyShutdown(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = createAndRunSleeperJob(story.j.jenkins, "durableAgainstClean", FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
                Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, run.getExecution().getDurabilityHint());
                logStart[0] = JenkinsRule.getLog(run);
                CpsFlowExecution exec = (CpsFlowExecution)(run.getExecution());

                // Ensure the storage file is unreadable
                try (FileChannel fis = new FileOutputStream(new File(exec.getStorageDir(), "flowNodeStore.xml")).getChannel()) {
                    fis.truncate(5); // Leave a tiny bit just to make things more interesting
                }
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = story.j.jenkins.getItemByFullName("durableAgainstClean", WorkflowJob.class).getLastBuild();
                verifyFailedCleanly(story.j.jenkins, run);
                story.j.assertLogContains(logStart[0], run);
            }
        });
    }

    /** Verify that if we bomb out because we cannot resume, we at least try to finish the flow graph if we have something to work with. */
    @Test
    @Ignore
    // Can be fleshed out later if we have a valid need for it.
    public void testPipelineFinishesFlowGraph() throws Exception {
        final String[] logStart = new String[1];
        final List<FlowNode> nodesOut = new ArrayList<>();
        story.addStepWithDirtyShutdown(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = createAndRunSleeperJob(story.j.jenkins, "durableAgainstClean", FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
                Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, run.getExecution().getDurabilityHint());
                logStart[0] = JenkinsRule.getLog(run);
                if (run.getExecution() instanceof CpsFlowExecution) {
                    // Pause and unPause to force persistence
                    CpsFlowExecution cpsFlow = (CpsFlowExecution)(run.getExecution());
                    cpsFlow.pause(true);
                    long timeout = System.nanoTime()+TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS);
                    while(System.nanoTime() < timeout && !cpsFlow.isPaused()) {
                        Thread.sleep(100L);
                    }
                    nodesOut.addAll(new DepthFirstScanner().allNodes(run.getExecution()));
                    nodesOut.sort(FlowScanningUtils.ID_ORDER_COMPARATOR);
                    cpsFlow.pause(false);
                    timeout = System.nanoTime()+TimeUnit.NANOSECONDS.convert(5, TimeUnit.SECONDS);
                    while(System.nanoTime() < timeout && cpsFlow.isPaused()) {
                        Thread.sleep(100L);
                    }

                    // Ensures we're marked as can-not-resume
                    cpsFlow.persistedClean = false;
                    cpsFlow.saveOwner();
                }
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = story.j.jenkins.getItemByFullName("durableAgainstClean", WorkflowJob.class).getLastBuild();
                verifyFailedCleanly(story.j.jenkins, run);
                story.j.assertLogContains(logStart[0], run);
                assertIncludesNodes(nodesOut, run);
            }
        });
    }

    /** Verify that we retain and flowgraph start with the included nodes, which must be in sorted order */
    void assertIncludesNodes(List<FlowNode> prefixNodes, WorkflowRun run) throws Exception {
        List<FlowNode> nodes = new DepthFirstScanner().allNodes(run.getExecution());
        nodes.sort(FlowScanningUtils.ID_ORDER_COMPARATOR);

        // Make sure we have the starting nodes at least
        assert prefixNodes.size() <= nodes.size();
        for (int i=0; i<prefixNodes.size(); i++) {
            try {
                FlowNode match = prefixNodes.get(i);
                FlowNode after = nodes.get(i);
                Assert.assertEquals(match.getDisplayFunctionName(), after.getDisplayFunctionName());
            } catch (Exception ex) {
                throw new Exception("Error with flownode at index="+i, ex);
            }
        }
    }

    /** Sanity check that fully durable pipelines shutdown and restart cleanly */
    @Test
    public void testFullyDurableSurvivesCleanRestart() throws Exception {
        final String jobName = "survivesEverything";
        final String[] logStart = new String[1];

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Jenkins jenkins = story.j.jenkins;
                WorkflowRun run = createAndRunBasicJob(story.j.jenkins, jobName, FlowDurabilityHint.MAX_SURVIVABILITY);
                FlowExecution exec = run.getExecution();
                if (exec instanceof CpsFlowExecution) {
                    assert ((CpsFlowExecution) exec).getStorage().isPersistedFully();
                }
                logStart[0] = JenkinsRule.getLog(run);
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = story.j.jenkins.getItemByFullName(jobName, WorkflowJob.class).getLastBuild();
                verifySafelyResumed(story.j, run, true, logStart[0]);
            }
        });
    }

    /**
     * Sanity check that fully durable pipelines can survive hard kills.
     */
    @Test
    public void testFullyDurableSurvivesDirtyRestart() throws Exception {
        final String jobName = "survivesEverything";
        final String[] logStart = new String[1];

        story.addStepWithDirtyShutdown(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Jenkins jenkins = story.j.jenkins;
                WorkflowRun run = createAndRunSleeperJob(story.j.jenkins, jobName, FlowDurabilityHint.MAX_SURVIVABILITY);
                FlowExecution exec = run.getExecution();
                if (exec instanceof CpsFlowExecution) {
                    assert ((CpsFlowExecution) exec).getStorage().isPersistedFully();
                }
                logStart[0] = JenkinsRule.getLog(run);
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = story.j.jenkins.getItemByFullName(jobName, WorkflowJob.class).getLastBuild();
                verifySafelyResumed(story.j, run, false, logStart[0]);
            }
        });
    }

    @Test
    public void testResumeBlocked() throws Exception {
        final String jobName = "survivesEverything";
        final String[] logStart = new String[1];
        final List<FlowNode> nodesOut = new ArrayList<>();

        story.addStepWithDirtyShutdown(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Jenkins jenkins = story.j.jenkins;
                WorkflowRun run = createAndRunSleeperJob(story.j.jenkins, jobName, FlowDurabilityHint.MAX_SURVIVABILITY);
                run.getParent().setResumeBlocked(true);
                FlowExecution exec = run.getExecution();
                if (exec instanceof CpsFlowExecution) {
                    assert ((CpsFlowExecution) exec).getStorage().isPersistedFully();
                }
                logStart[0] = JenkinsRule.getLog(run);
                nodesOut.addAll(new DepthFirstScanner().allNodes(run.getExecution()));
                nodesOut.sort(FlowScanningUtils.ID_ORDER_COMPARATOR);
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = story.j.jenkins.getItemByFullName(jobName, WorkflowJob.class).getLastBuild();
                verifyFailedCleanly(story.j.jenkins, run);
                assertIncludesNodes(nodesOut, run);
            }
        });
    }

    @Test
    @Issue("JENKINS-49961")
    public void testResumeBlockedAddedAfterRunStart() throws Exception {
        final String jobName = "survivesEverything";
        final String[] logStart = new String[1];
        final List<FlowNode> nodesOut = new ArrayList<>();

        story.addStepWithDirtyShutdown(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Jenkins jenkins = story.j.jenkins;
                WorkflowRun run = createAndRunSleeperJob(story.j.jenkins, jobName, FlowDurabilityHint.MAX_SURVIVABILITY);
                run.getParent().setResumeBlocked(false);
                FlowExecution exec = run.getExecution();
                if (exec instanceof CpsFlowExecution) {
                    assert ((CpsFlowExecution) exec).getStorage().isPersistedFully();
                }
                logStart[0] = JenkinsRule.getLog(run);
                nodesOut.addAll(new DepthFirstScanner().allNodes(run.getExecution()));
                nodesOut.sort(FlowScanningUtils.ID_ORDER_COMPARATOR);
                run.getParent().setResumeBlocked(true);
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = story.j.jenkins.getItemByFullName(jobName, WorkflowJob.class).getLastBuild();
                verifyFailedCleanly(story.j.jenkins, run);
                assertIncludesNodes(nodesOut, run);
            }
        });
    }

    private static void assertBuildNotHung(@Nonnull RestartableJenkinsRule story, @Nonnull  WorkflowRun run, int timeOutMillis) throws Exception {
        if (run.isBuilding()) {
            story.j.waitUntilNoActivityUpTo(timeOutMillis);
        }
    }

    /** Launches the job used for fuzzing in the various timed fuzzing tests to catch timing-sensitive issues -- including the timeout. */
    private WorkflowRun runFuzzerJob(JenkinsRule jrule, String jobName, FlowDurabilityHint hint) throws Exception {
        Jenkins jenkins = jrule.jenkins;
        WorkflowJob job = jenkins.getItemByFullName(jobName, WorkflowJob.class);
        if (job == null) {  // Job may already have been created
            job = jenkins.createProject(WorkflowJob.class, jobName);
            job.addProperty(new DurabilityHintJobProperty(hint));
            job.setDefinition(new CpsFlowDefinition(
                    "echo 'first'\n" +
                            "def steps = [:]\n" +
                            "steps['1'] = {\n" +
                            "    echo 'do 1 stuff'\n" +
                            "}\n" +
                            "steps['2'] = {\n" +
                            "    echo '2a'\n" +
                            "    echo '2b'\n" +
                            "    def nested = [:]\n" +
                            "    nested['2-1'] = {\n" +
                            "        echo 'do 2-1'\n" +
                            "    } \n" +
                            "    nested['2-2'] = {\n" +
                            "        sleep 1\n" +
                            "        echo '2 section 2'\n" +
                            "    }\n" +
                            "    parallel nested\n" +
                            "}\n" +
                            "parallel steps\n" +
                            "echo 'final'", false
            ));
        }

        // First we need to build the job to get an appropriate estimate for how long we need to wait before hard-restarting Jenkins in order to catch it in the middle
        story.j.buildAndAssertSuccess(job);
        long millisDuration = job.getLastBuild().getDuration();
        System.out.println("Test fuzzer job in  completed in "+millisDuration+" ms");

        // Now we run the job again and wait an appropriate amount of time -- but we return the job so tests can grab info before restarting.
        int time = new Random().nextInt((int) millisDuration);
        System.out.println("Starting fuzzer job and waiting "+time+" ms before restarting.");
        WorkflowRun run = job.scheduleBuild2(0).getStartCondition().get();
        run.getExecutionPromise().get();  // Ensures run has begun so that it *can* complete cleanly.
        Thread.sleep(time);
        return run;
    }

    /** Test interrupting build by randomly dying at unpredictable times.
     * May fail rarely due to files being copied in a different order than they are modified as part of simulating a dirty restart.
     * See {@link RestartableJenkinsRule#simulateAbruptShutdown()} for why that copying happens. */
    @Test
    @Ignore //Too long to run as part of main suite
    @TimedRepeatRule.RepeatForTime(repeatMillis = 150_000)
    public void fuzzTimingDurable() throws Exception {
        final String jobName = "NestedParallelDurableJob";
        final String[] logStart = new String[1];
        final List<FlowNode> nodesOut = new ArrayList<>();
        final int[] buildNumber = new int [1];

        // Create thread that eventually interrupts Jenkins with a hard shutdown at a random time interval
        story.addStepWithDirtyShutdown(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = runFuzzerJob(story.j, jobName, FlowDurabilityHint.MAX_SURVIVABILITY);
                logStart[0] = JenkinsRule.getLog(run);
                nodesOut.clear();
                nodesOut.addAll(new DepthFirstScanner().allNodes(run.getExecution()));
                nodesOut.sort(FlowScanningUtils.ID_ORDER_COMPARATOR);
                if (run.getExecution() != null) {
                    Assert.assertEquals(FlowDurabilityHint.MAX_SURVIVABILITY, run.getExecution().getDurabilityHint());
                }
            }
            });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = story.j.jenkins.getItemByFullName(jobName, WorkflowJob.class).getLastBuild();
                if (run == null) {   // Build killed so early the Run did not get to persist
                    return;
                }
                if (run.getExecution() != null) {
                    Assert.assertEquals(FlowDurabilityHint.MAX_SURVIVABILITY, run.getExecution().getDurabilityHint());
                }
                if (run.isBuilding()) {
                    assertBuildNotHung(story, run, 30_000);
                    Assert.assertEquals(Result.SUCCESS, run.getResult());
                }
                verifyCompletedCleanly(story.j.jenkins, run);
                assertIncludesNodes(nodesOut, run);
                story.j.assertLogContains(logStart[0], run);
            }
        });

    }

    /** Test interrupting build by randomly dying at unpredictable times.
     *  May fail rarely due to files being copied in a different order than they are modified as part of simulating a dirty restart.
     *  See {@link RestartableJenkinsRule#simulateAbruptShutdown()} for why that copying happens. */
    @Test
    @Ignore //Too long to run as part of main suite
    @TimedRepeatRule.RepeatForTime(repeatMillis = 150_000)
    public void fuzzTimingNonDurableWithDirtyRestart() throws Exception {
        final String jobName = "NestedParallelDurableJob";
        final String[] logStart = new String[1];

        // Create thread that eventually interrupts Jenkins with a hard shutdown at a random time interval
        story.addStepWithDirtyShutdown(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = runFuzzerJob(story.j, jobName, FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
                logStart[0] = JenkinsRule.getLog(run);
                if (run.getExecution() != null) {
                    Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, run.getExecution().getDurabilityHint());
                }
                if (run.isBuilding()) {
                    Assert.assertNotEquals(Boolean.TRUE, ((CpsFlowExecution)run.getExecution()).persistedClean);
                } else {
                    Assert.assertEquals(Boolean.TRUE, ((CpsFlowExecution)run.getExecution()).persistedClean);
                }
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = story.j.jenkins.getItemByFullName(jobName, WorkflowJob.class).getLastBuild();
                if (run == null) {   // Build killed so early the Run did not get to persist
                    return;
                }
                if (run.getExecution() != null) {
                    Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, run.getExecution().getDurabilityHint());
                }
                assertBuildNotHung(story, run, 30_000);
                verifyCompletedCleanly(story.j.jenkins, run);
                story.j.assertLogContains(logStart[0], run);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                // Verify build doesn't resume at next restart, see JENKINS-50199
                Assert.assertFalse(FlowExecutionList.get().iterator().hasNext());
                WorkflowRun run = story.j.jenkins.getItemByFullName(jobName, WorkflowJob.class).getLastBuild();
                if (run == null) {
                    return;
                }
                Assert.assertFalse(run.isBuilding());
                Assert.assertTrue(run.getExecution().isComplete());
                if (run.getExecution() instanceof  CpsFlowExecution) {
                    assert ((CpsFlowExecution)(run.getExecution())).done;
                }
            }
        });

    }

    /** Test interrupting build by randomly restarting *cleanly* at unpredictable times and verify we stick pick up and resume. */
    @Test
    @Ignore //Too long to run as part of main suite
    @TimedRepeatRule.RepeatForTime(repeatMillis = 150_000)
    public void fuzzTimingNonDurableWithCleanRestart() throws Exception {

        final String jobName = "NestedParallelDurableJob";
        final String[] logStart = new String[1];
        final List<FlowNode> nodesOut = new ArrayList<>();

        // Create thread that eventually interrupts Jenkins with a hard shutdown at a random time interval
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = runFuzzerJob(story.j, jobName, FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
                logStart[0] = JenkinsRule.getLog(run);
                if (run.getExecution() != null) {
                    Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, run.getExecution().getDurabilityHint());
                }
                nodesOut.clear();
                nodesOut.addAll(new DepthFirstScanner().allNodes(run.getExecution()));
                nodesOut.sort(FlowScanningUtils.ID_ORDER_COMPARATOR);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowRun run = story.j.jenkins.getItemByFullName(jobName, WorkflowJob.class).getLastBuild();
                if (run == null) {   // Build killed so early the Run did not get to persist
                    return;
                }
                if (run.getExecution() != null) {
                    Assert.assertEquals(FlowDurabilityHint.PERFORMANCE_OPTIMIZED, run.getExecution().getDurabilityHint());
                }
                if (run.isBuilding()) {
                    assertBuildNotHung(story, run, 30_000);
                }
                verifyCompletedCleanly(story.j.jenkins, run);
                story.j.assertLogContains(logStart[0], run);
                if (run.isBuilding()) {
                    try {
                        story.j.waitUntilNoActivityUpTo(30_000);
                    } catch (AssertionError ase) {
                        throw new AssertionError("Build hung: "+run, ase);
                    }
                }
                Assert.assertEquals(Result.SUCCESS, run.getResult());
                assertIncludesNodes(nodesOut, run);
            }
        });
    }
}
