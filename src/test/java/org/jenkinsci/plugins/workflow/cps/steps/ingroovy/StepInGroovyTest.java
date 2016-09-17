package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 * @see "doc/step-in-groovy.md"
 */
public class StepInGroovyTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    /**
     * Invokes helloWorldGroovy.groovy in resources/stepsInGroovy and make sure that works
     */
    @Test
    public void helloWorld() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "demo");
        p.setDefinition(new CpsFlowDefinition("helloWorldGroovy('Duke') { echo 'Hello body' }"));
        WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertLogContains("Hello Duke",b);
        j.assertLogContains("Hello body",b);
        j.assertLogContains("Good bye Duke",b);
    }
}
