package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

/**
 * @author Kohsuke Kawaguchi
 * @see "doc/step-in-groovy.md"
 */
public class StepInGroovyTest {
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    /**
     * Invokes helloWorldGroovy.groovy in resources/stepsInGroovy and make sure that works.
     *
     * Restart Jenkins in the middle to make sure the resurrection works as expected.
     */
    @Test
    public void helloWorld() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                        "helloWorldGroovy('Duke') {\n" +
                            "echo 'Hello body'\n"+
                            "semaphore 'restart'\n"+
                            "echo 'Good bye body'\n"+
                        "}"));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("restart/1", b);
                story.j.waitForMessage("Hello body",b);
                story.j.assertLogContains("Hello Duke",b);
                story.j.assertLogContains("Hello body",b);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("demo", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                SemaphoreStep.success("restart/1", null);
                story.j.waitForCompletion(b);

                story.j.assertLogContains("Hello Duke",b);
                story.j.assertLogContains("Hello body",b);
                story.j.assertLogContains("Good bye body",b);
                story.j.assertLogContains("Good bye Duke",b);
            }
        });
    }
}
