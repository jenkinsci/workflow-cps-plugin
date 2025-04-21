package org.jenkinsci.plugins.workflow.cps;

import hudson.model.Action;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.not;

public class RunningFlowActionsTest {

    // JenkinsRule required for initialization of TransientActionFactory cache
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test public void noRunningFlowActionsIfTypeIrrelevant() {
        assertThat(TransientActionFactory.factoriesFor(WorkflowRun.class, TestNotARunningFlowAction.class),
            not(hasItem(instanceOf(RunningFlowActions.class))));
        assertThat(TransientActionFactory.factoriesFor(FlowExecutionOwner.Executable.class, TestNotARunningFlowAction.class),
            not(hasItem(instanceOf(RunningFlowActions.class))));
        assertThat(TransientActionFactory.factoriesFor(FlowExecutionOwner.Executable.class, RunningFlowAction.class),
            hasItems(instanceOf(RunningFlowActions.class)));
    }

    @TestExtension("testNotARunningFlowAction")
    public static class TestNotARunningFlowAction implements Action {

        @Override
        public String getIconFileName() {
            return "";
        }

        @Override
        public String getDisplayName() {
            return "";
        }

        @Override
        public String getUrlName() {
            return "";
        }
    }
}
