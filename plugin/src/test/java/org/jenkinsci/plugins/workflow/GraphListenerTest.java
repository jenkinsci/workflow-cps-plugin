package org.jenkinsci.plugins.workflow;

import static org.hamcrest.Matchers.is;

import hudson.ExtensionList;
import hudson.model.Run;
import java.io.IOException;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

import java.io.Serializable;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.rules.ErrorCollector;
import org.jvnet.hudson.test.Issue;
import static org.awaitility.Awaitility.await;

public class GraphListenerTest
{
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule();

    @Rule
    public ErrorCollector errors = new ErrorCollector();

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

    @TestExtension("listener")
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

    @Test
    public void listenersRunBeforeBuildCompletion() throws Exception {
        var listener = ExtensionList.lookupSingleton(CheckBuildCompletionListener.class);
        listener.errors = errors;
        var p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("echo 'test'", true));
        var b = r.buildAndAssertSuccess(p);
        await().until(() -> listener.done);
    }

    @TestExtension("listenersRunBeforeBuildCompletion")
    public static class CheckBuildCompletionListener implements GraphListener {
        private ErrorCollector errors;
        private boolean done;

        @Override
        public void onNewHead(FlowNode node) {
            if (node instanceof FlowEndNode) {
                try {
                    var b = (WorkflowRun) node.getExecution().getOwner().getExecutable();
                    errors.checkThat("Listeners should always run before build completion", b.isLogUpdated(), is(true));
                } catch (IOException e) {
                    errors.addError(e);
                }
                done = true;
            }
        }
    }
}
