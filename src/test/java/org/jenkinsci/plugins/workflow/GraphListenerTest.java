package org.jenkinsci.plugins.workflow;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.Serializable;
import java.util.Random;

public class GraphListenerTest
{
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Test
    public void listener() throws Exception {
        String script = "node { \n" //
            + "    echo \"hello\"\n" //
            + "  "
            + "}";
        WorkflowJob j = r.createProject(WorkflowJob.class, "listener");
        j.setDefinition(new CpsFlowDefinition(script, true));
        r.buildAndAssertSuccess(j);
    }

    @TestExtension
    public static class TestGraphListener implements GraphListener, Serializable
    {
        private Random random = new Random();

        @Override
        public void onNewHead( FlowNode flowNode )
        {
            Assert.assertNotNull(flowNode.getDisplayName());
            Assert.assertNotNull(flowNode.getExecution());
            if(random.nextBoolean()){
                throw new NullPointerException( "a kind of problem here" );
            }
        }
    }
}
