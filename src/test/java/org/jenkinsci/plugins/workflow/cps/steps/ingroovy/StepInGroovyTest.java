package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.StaticWhitelist;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.io.IOException;

import static org.junit.Assert.*;

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

    /**
     * Invokes helloWorldGroovy.groovy in resources/stepsInGroovy and make sure that works.
     *
     * Restart Jenkins in the middle to make sure the resurrection works as expected.
     */
    @Test
    public void security() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.createProject(WorkflowJob.class, "demo");

                // Groovy step should have access to the money in the vault
                p.setDefinition(new CpsFlowDefinition(
                        "assert bankTeller()==2000",
                        true));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));

                // but no direct access allowed
                String className = BankTellerSecureStepExecution.class.getName().replace("Secure","");
                p.setDefinition(new CpsFlowDefinition(
                        "def o = new "+ className +"()\n"+
                        "echo '$$$instantiated'\n"+
                        "echo o.moneyInVault as String\n"+
                        "echo '$$$stole money'\n",
                        true
                ));
                WorkflowRun b = p.scheduleBuild2(0).get();
                story.j.assertBuildStatus(Result.FAILURE, b);
                story.j.assertLogContains("$$$instantiated",b);
                story.j.assertLogNotContains("$$$stole money",b);
                story.j.assertLogContains(
                        StaticWhitelist.rejectField(BankTellerSecureStepExecution.class.getDeclaredField("moneyInVault")).getMessage(),
                        b);
            }
        });
    }

    /**
     * Let's invoke the body multiple times, serial and parallel.
     */
    @Test
    public void repeatedInvocations() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.createProject(WorkflowJob.class, "demo");
                WorkflowRun b;

                p.setDefinition(new CpsFlowDefinition(
                        "loop(3) {\n" +
                            "echo 'Hello'\n"+
                        "}"));
                b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                shouldHaveHello(3,b);

                p.setDefinition(new CpsFlowDefinition(
                        "loop(0) {\n" +
                            "echo 'Hello'\n"+
                        "}"));
                b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                shouldHaveHello(0,b);

                // parallel execution of the body

                p.setDefinition(new CpsFlowDefinition(
                        "loop(count:3,parallel:true) {\n" +
                            "semaphore 'branch'\n"+
                        "}"));
                QueueTaskFuture<WorkflowRun> f = p.scheduleBuild2(0);
                b = f.getStartCondition().get();

                SemaphoreStep.waitForStart("branch/3",b);
                SemaphoreStep.success("branch/1",null);
                SemaphoreStep.success("branch/2",null);
                SemaphoreStep.success("branch/3",null);
                story.j.assertBuildStatusSuccess(f);
            }

            private void shouldHaveHello(int count, WorkflowRun b) throws IOException {
                assertEquals(count+1,b.getLog().split("Hello").length);
            }
        });
    }
}
