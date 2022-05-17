package org.jenkinsci.plugins.workflow.cps;

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
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
}
