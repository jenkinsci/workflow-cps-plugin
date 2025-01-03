package org.jenkinsci.plugins.workflow.cps;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.ExtensionList;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Set;
import java.util.logging.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

public class CpsStepContextTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public LoggerRule logger = new LoggerRule();

    @Issue("JENKINS-75067")
    @Test
    public void failingStepListenerNotLeakClosures() throws Exception {
        // Even before the fix there's only one warning logged.  Asserting zero records is probably over-stepping,
        // but asserting just one record with our target message risks a false negative (some other unrelated message
        // being first, and our being later).
        logger.record(CpsThreadGroup.class, Level.WARNING).capture(10);
        WorkflowJob job = r.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("node {}\n", true));

        WorkflowRun build = r.buildAndAssertStatus(Result.FAILURE, job);
        r.assertLogContains("oops", build);
        assertThat(ClosureCounter.get().closureCount, equalTo(0));
        assertThat(logger.getMessages(), not(hasItem(containsString("Stale closure"))));
    }

    @TestExtension("failingStepListenerNotLeakClosures")
    public static class FailingStepListener implements StepListener {

        @Override
        public void notifyOfNewStep(@NonNull Step s, @NonNull StepContext context) {
            context.onFailure(new AbortException("oops"));
        }
    }

    @Issue("JENKINS-75067")
    @Test
    public void executionStartExceptionNotLeakClosures() throws Exception {
        logger.record(CpsThreadGroup.class, Level.WARNING).capture(10);
        WorkflowJob job = r.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("badBlock {}\n", true));

        WorkflowRun build = r.buildAndAssertStatus(Result.FAILURE, job);
        r.assertLogContains("oops", build);
        assertThat(ClosureCounter.get().closureCount, equalTo(0));
        assertThat(logger.getMessages(), not(hasItem(containsString("Stale closure"))));
    }

    @Issue("JENKINS-75067")
    @Test
    public void executionWithBodyRunningSyncNotLeakClosures() throws Exception {
        logger.record(CpsThreadGroup.class, Level.WARNING).capture(10);
        WorkflowJob job = r.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("echo passthrough {}\n", true));

        WorkflowRun build = r.buildAndAssertSuccess(job);
        r.assertLogContains("hooray", build);
        assertThat(ClosureCounter.get().closureCount, equalTo(0));
        assertThat(logger.getMessages(), not(hasItem(containsString("Stale closure"))));
    }

    public static class BadBlockStep extends Step {

        @DataBoundConstructor
        public BadBlockStep() {}

        @Override
        public StepExecution start(StepContext context) throws Exception {
        return StepExecutions.synchronous(context, ctx -> {
            throw new AbortException("oops");
        });
        }

        @TestExtension("executionStartExceptionNotLeakClosures")
        public static class DescriptorImpl extends StepDescriptor {

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Set.of();
            }

            @Override
            public String getFunctionName() {
                return "badBlock";
            }

            @Override
            public boolean takesImplicitBlockArgument() {
                return true;
            }
        }
    }

    public static class PassthroughStep extends Step {

        @DataBoundConstructor
        public PassthroughStep() {}

        @Override
        public StepExecution start(StepContext context) throws Exception {
            return StepExecutions.synchronous(context, ctx -> {
                return "hooray";
            });
        }

        @TestExtension("executionWithBodyRunningSyncNotLeakClosures")
        public static class DescriptorImpl extends StepDescriptor {

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Set.of();
            }

            @Override
            public String getFunctionName() {
                return "passthrough";
            }

            @Override
            public boolean takesImplicitBlockArgument() {
                return true;
            }
        }
    }

    @TestExtension
    public static class ClosureCounter implements GraphListener.Synchronous {
        int closureCount = -1;

        @Override
        public void onNewHead(FlowNode node) {
            // this only works using a Synchronous listener, otherwise the fall-back closure cleaning
            // will have already executed prior to receiving this event
            if (node instanceof FlowEndNode) {
                try {
                    closureCount = ((CpsFlowExecution) node.getExecution()).programPromise.get().closures.size();
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        static ClosureCounter get() {
            return ExtensionList.lookupSingleton(ClosureCounter.class);
        }
    }
}
