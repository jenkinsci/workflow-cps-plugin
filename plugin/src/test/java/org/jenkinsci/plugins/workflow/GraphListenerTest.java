package org.jenkinsci.plugins.workflow;

import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.Serializable;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import org.jvnet.hudson.test.Issue;

public class GraphListenerTest
{
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule();

    private static final String LOG_MESSAGE = "some problem here";

    @Issue("JENKINS-54890")
    @Test
    public void listener()
        throws Exception
    {
        logging.record( CpsFlowExecution.class, Level.WARNING).capture(200);
        String script = "node { \n" //
            + "    echo \"hello\"\n" //
            + "  " + "}";
        WorkflowJob j = r.createProject( WorkflowJob.class, "listener" );
        j.setDefinition( new CpsFlowDefinition( script, true ) );
        Run run = r.buildAndAssertSuccess( j );
        List<String> logs = logging.getMessages();
        long found = logs.stream().filter( s -> s.contains( LOG_MESSAGE ) ).count();
        Assert.assertTrue( "cannot find listener exception message", found > 0 );
    }

    @TestExtension
    public static class TestGraphListener
        implements GraphListener, Serializable
    {
        private Random random = new Random();

        @Override
        public void onNewHead( FlowNode flowNode )
        {
            Assert.assertNotNull( flowNode.getDisplayName() );
            Assert.assertNotNull( flowNode.getExecution() );
            throw new NullPointerException( LOG_MESSAGE );
        }
    }
}
