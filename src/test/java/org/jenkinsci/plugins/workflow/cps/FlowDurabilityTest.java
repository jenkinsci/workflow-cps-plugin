package org.jenkinsci.plugins.workflow.cps;

import hudson.model.Result;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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

    static WorkflowRun createAndRunBasicJob(Jenkins jenkins, String jobName, FlowDurabilityHint durabilityHint) throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, jobName);
        CpsFlowDefinition def = new CpsFlowDefinition("node {\n " +
                "semaphore 'halt' \n" +
                "} \n" +
                "echo 'I like chese'", false);
        def.setDurabilityHint(durabilityHint);
        job.setDefinition(def);
        WorkflowRun run = job.scheduleBuild2(0).getStartCondition().get();
        SemaphoreStep.waitForStart("halt/1", run);
        return run;
    }

    static void verifyCompletedCleanly(WorkflowRun run) throws Exception {
        Assert.assertEquals(Result.SUCCESS, run.getResult());
        FlowExecution exec = run.getExecution();

        // Confirm the flow graph is navigable and contains the heads with appropriate ending
        DepthFirstScanner scan = new DepthFirstScanner();
        List<FlowNode> allNodes = scan.allNodes(exec);
        Assert.assertEquals(1, exec.getCurrentHeads().size());
        Assert.assertEquals(FlowEndNode.class, exec.getCurrentHeads().get(0).getClass());
        assert allNodes.contains(exec.getCurrentHeads().get(0));
    }

    /**
     * Confirm that for ALL implementations, a run can complete and be loaded if you restart after completion.
     */
    @Test
    public void testCompleteAndLoadBuilds() throws Exception {
        final FlowDurabilityHint[] durabilityHints = FlowDurabilityHint.values();
        final WorkflowJob[] jobs = new WorkflowJob[durabilityHints.length];

        // Create and run jobs for each of the durability hints
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                int i = 1;
                for (FlowDurabilityHint hint : durabilityHints) {
                    try{
                        WorkflowRun run = createAndRunBasicJob(story.j.jenkins, "basicJob-"+hint.toString(), hint);
                        jobs[i-1] = run.getParent();
                        SemaphoreStep.success("halt/"+i++,Result.SUCCESS);
                        story.j.waitForCompletion(run);
                        story.j.assertBuildStatus(Result.SUCCESS, run);
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
                for (WorkflowJob j : jobs) {
                    try{
                        WorkflowRun run = j.getLastBuild();
                        verifyCompletedCleanly(run);
                    } catch (AssertionError ae) {
                        System.out.println("Error with durability level: "+j.getDefinition().getDurabilityHint());
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

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Jenkins jenkins = story.j.jenkins;
                createAndRunBasicJob(story.j.jenkins, jobName, FlowDurabilityHint.SURVIVE_CLEAN_RESTART);
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Jenkins jenkins = story.j.jenkins;
                WorkflowJob job = jenkins.getItemByFullName(jobName, WorkflowJob.class);
                WorkflowRun run = job.getLastBuild();
                assert run.isBuilding();
                FlowExecution exec = run.getExecution();

                // Assert that we have the appropriate flow graph entries
                List<FlowNode> heads = exec.getCurrentHeads();
                Assert.assertEquals(1, heads.size());
                Assert.assertEquals("semaphore", heads.get(0).getDisplayFunctionName());

                SemaphoreStep.success("halt/1", Result.SUCCESS);
                story.j.waitForCompletion(run);
                verifyCompletedCleanly(run);
            }
        });
    }


    /** Verify that if the master dies messily and we're not durable against that, build fails cleanly.
     */
    @Test
    public void testDurableAgainstCleanRestartFailsWithDirtyShutdown() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                createAndRunBasicJob(story.j.jenkins, "durableAgainstClean", FlowDurabilityHint.SURVIVE_CLEAN_RESTART);
                simulateAbruptFailure(story);
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Jenkins jenkins = story.j.jenkins;
                WorkflowJob job = jenkins.getItemByFullName("durableAgainstClean", WorkflowJob.class);
                WorkflowRun run = job.getLastBuild();
                assert !run.isBuilding();
                assert run.getResult() == Result.FAILURE || run.getResult() == Result.ABORTED;

                FlowExecution exec = run.getExecution();

                // Assert that we have the appropriate flow graph entries
                List<FlowNode> heads = exec.getCurrentHeads();
                Assert.assertEquals(1, heads.size());
            }
        });
    }

    /** Verify that if the master dies messily and we're not durable against restarts, the build fails somewhat cleanly.
     */
    @Test
    public void testNotDurableFailsOnDirtyRestart() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                createAndRunBasicJob(story.j.jenkins, "nonDurable", FlowDurabilityHint.NO_PROMISES);
                simulateAbruptFailure(story);
            }
        });

        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                Jenkins jenkins = story.j.jenkins;
                WorkflowJob job = jenkins.getItemByFullName("nonDurable", WorkflowJob.class);
                WorkflowRun run = job.getLastBuild();
                assert !run.isBuilding();
                assert run.getResult() == Result.FAILURE || run.getResult() == Result.ABORTED;

                FlowExecution exec = run.getExecution();

                // Assert that we have the appropriate flow graph entries
                List<FlowNode> heads = exec.getCurrentHeads();
                Assert.assertEquals(1, heads.size());
            }
        });
    }

    /** Sanity check that fully durable pipelines shutdown and restart cleanly */
    @Test
    public void testFullyDurableSurvivesCleanRestart() throws Exception {

    }

    /**
     * Sanity check that fully durable pipelines can survive hard kills.
     */
    @Test
    public void testFullyDurableSurvivesDirtyRestart() throws Exception {

    }

    /** https://stackoverflow.com/questions/6214703/copy-entire-directory-contents-to-another-directory */
    public static class CopyFileVisitor extends SimpleFileVisitor<Path> {
        private final Path targetPath;
        private Path sourcePath = null;
        public CopyFileVisitor(Path targetPath) {
            this.targetPath = targetPath;
        }

        @Override
        public FileVisitResult preVisitDirectory(final Path dir,
                                                 final BasicFileAttributes attrs) throws IOException {
            if (sourcePath == null) {
                sourcePath = dir;
            } else {
                Files.createDirectories(targetPath.resolve(sourcePath
                        .relativize(dir)));
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(final Path file,
                                         final BasicFileAttributes attrs) throws IOException {
            if (!Files.isSymbolicLink(file)) {
                // Needed because Jenkins includes invalid lastSuccessful symlinks and otherwise we get a NoSuchFileException
                Files.copy(file,
                        targetPath.resolve(sourcePath.relativize(file)));
            } else if (Files.isSymbolicLink(file) && Files.exists(Files.readSymbolicLink(file))) {
                Files.copy(file,
                        targetPath.resolve(sourcePath.relativize(file)));
            }
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * Simulate an abrupt failure of Jenkins to see if it appropriately handles inconsistent states when
     *  shutdown cleanup is not performed or data is not written fully to disk.
     *
     * Works by copying the JENKINS_HOME to a new directory and then setting the {@link RestartableJenkinsRule} to use
     * that for the next restart. Thus we only have the data actually persisted to disk at that time to work with.
     *
     * Should be run as the last part of a {@link org.jvnet.hudson.test.RestartableJenkinsRule.Step}.
     *
     * @param rule Restartable JenkinsRule to use for simulating failure and restart
     * @throws IOException
     */
    public static void simulateAbruptFailure(RestartableJenkinsRule rule) throws IOException {
        File homeDir = rule.home;
        TemporaryFolder temp = new TemporaryFolder();
        temp.create();
        File newHome = temp.newFolder();

        // Copy efficiently
        Files.walkFileTree(homeDir.toPath(), Collections.EMPTY_SET, 99, new CopyFileVisitor(newHome.toPath()));


        rule.home = newHome;
    }
}
