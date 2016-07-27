package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * @author Kohsuke Kawaguchi
 */
public class StepInGroovyTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    static {
        try {
            // fake content root at src/test/resources/stepsInGroovy
            URL u = StepInGroovyTest.class.getResource("/stepsInGroovy/META-INF/index");
            GroovyCompiler.additionalContentRoots.add(new URL(u,"../"));
        } catch (MalformedURLException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Invokes helloWorldGroovy.groovy in resources/stepsInGroovy and make sure that works
     */
    @Test
    public void helloWorld() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "demo");
        p.setDefinition(new CpsFlowDefinition("helloWorldGroovy 'Duke'"));
        WorkflowRun b = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertLogContains("Hello Duke",b);
    }
}
