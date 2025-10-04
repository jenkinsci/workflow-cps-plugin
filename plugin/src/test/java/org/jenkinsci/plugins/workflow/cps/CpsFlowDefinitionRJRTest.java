package org.jenkinsci.plugins.workflow.cps;

import hudson.Functions;
import java.util.logging.Level;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RealJenkinsRule;

public class CpsFlowDefinitionRJRTest {

    @Rule
    public RealJenkinsRule rjr = new RealJenkinsRule();

    @Test
    public void smokes() throws Throwable {
        rjr.then(CpsFlowDefinitionRJRTest::doesItSmoke);
    }

    private static void doesItSmoke(JenkinsRule r) throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("print Jenkins.get().getRootDir().toString()", false));
        r.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    @Test
    public void flushLogsOnShutdown() throws Throwable {
        Assume.assumeFalse(
                "RealJenkinsRule does not shut down Jenkins cleanly on Windows, see https://github.com/jenkinsci/jenkins-test-harness/pull/559",
                Functions.isWindows());
        rjr.withLogger(CpsFlowExecution.class, Level.FINER);
        rjr.then(CpsFlowDefinitionRJRTest::flushLogsOnShutdownPreRestart);
        rjr.then(CpsFlowDefinitionRJRTest::flushLogsOnShutdownPostRestart);
    }

    private static void flushLogsOnShutdownPreRestart(JenkinsRule r) throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("sleep 10\n", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        r.waitForMessage("Sleeping for 10 sec", b);
        r.jenkins.doQuietDown();
    }

    private static void flushLogsOnShutdownPostRestart(JenkinsRule r) throws Exception {
        WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
        WorkflowRun b = p.getLastBuild();
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        r.assertLogContains("Resuming build at ", b);
        r.assertLogContains("Pausing (Preparing for shutdown)", b);
    }
}
