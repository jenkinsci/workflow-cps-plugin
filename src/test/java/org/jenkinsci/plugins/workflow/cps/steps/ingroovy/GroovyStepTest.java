package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import hudson.model.queue.QueueTaskFuture;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.StaticWhitelist;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.nodes.StepNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.RestartableJenkinsRule;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static hudson.model.Result.*;
import static org.junit.Assert.*;

/**
 * @author Kohsuke Kawaguchi
 * @see "doc/step-in-groovy.md"
 */
public class GroovyStepTest {
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
                        "}",true));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("restart/1", b);
                story.j.waitForMessage("Hello body",b);
                story.j.assertLogContains("Hello Duke",b);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("demo", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                SemaphoreStep.success("restart/1", null);
                story.j.waitForCompletion(b);

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
                story.j.assertBuildStatus(FAILURE, b);
                story.j.assertLogContains("$$$instantiated",b);
                story.j.assertLogNotContains("$$$stole money",b);
                story.j.assertLogContains(
                        StaticWhitelist.rejectField(BankTellerSecureStepExecution.class.getDeclaredField("moneyInVault")).getMessage(),
                        b);
            }
        });
    }

    /**
     * Invokes the body multiple times, serial and parallel.
     * Step has a return value.
     */
    @Test
    public void repeatedInvocations_and_returnValue() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.createProject(WorkflowJob.class, "demo");
                WorkflowRun b;

                p.setDefinition(new CpsFlowDefinition(
                        "assert 3==loop(3) {\n" +
                            "echo 'Hello'\n"+
                        "}"));
                b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                shouldHaveHello(3,b);

                p.setDefinition(new CpsFlowDefinition(
                        "assert 0==loop(0) {\n" +
                            "echo 'Hello'\n"+
                        "}"));
                b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                shouldHaveHello(0,b);

                // parallel execution of the body

                p.setDefinition(new CpsFlowDefinition(
                        "assert 3==loop(count:3,parallel:true) {\n" +
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

    /**
     * Use of fields/methods in {@link GroovyStepExecution}
     * Call other steps that take closure.
     *
     * @see ComplexStepExecution
     */
    @Test
    public void complex() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.createProject(WorkflowJob.class, "demo");
                WorkflowRun b;

                p.setDefinition(new CpsFlowDefinition(
                        "def p = [$class:'BooleanParameterDefinition',name:'production',description:'check']\n"+
                        "assert 'foo'==complex(numbers:[1,2,3,4], param:p) {\n" +
                        "  echo '42'\n" +
                        "  return 'foo'\n" +
                        "}"
                ));
                b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("sum=10",b);
                story.j.assertLogContains("parameterName=production",b);
                story.j.assertLogContains("42",b);
            }
        });
    }

    /**
     * Tests the behaviour of exception coming in and out of a groovy step
     */
    @Test
    public void exception() throws Exception {
        story.addStep(new Statement() {
            WorkflowJob p;
            WorkflowRun b;

            @Override
            public void evaluate() throws Throwable {
                p = story.j.createProject(WorkflowJob.class, "demo");

                fromGroovyStepToCaller();
                passThrough();
                fromBodyToGroovyStep();
            }

            private void fromGroovyStepToCaller() throws Exception {
                p.setDefinition(new CpsFlowDefinition(
                    "import "+LifeIsToughException.class.getName()+"\n" +
                    "try {\n" +
                    "  exception('fromGroovyStepToCaller'){}\n" +
                    "  fail\n"+
                    "} catch (LifeIsToughException e) {\n" +
                    "  echo 'Caught='+e.message\n"+
                    "}"
                ));
                b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("Caught=Jesse wants so many test cases",b);
            }

            private void passThrough() throws Exception {
                p.setDefinition(new CpsFlowDefinition(
                    "import "+LifeIsToughException.class.getName()+"\n" +
                    "try {\n" +
                    "  exception('passThrough'){\n" +
                    "    throw new LifeIsToughException('There is not enough bacon')\n" +
                    "  }\n" +
                    "  fail\n"+
                    "} catch (LifeIsToughException e) {\n" +
                    "  echo 'Caught='+e.message\n"+
                    "}"
                ));
                b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("Caught=There is not enough bacon",b);
            }

            private void fromBodyToGroovyStep() throws Exception {
                p.setDefinition(new CpsFlowDefinition(
                    "import "+LifeIsToughException.class.getName()+"\n" +
                    "echo 'Reported='+exception('fromBodyToGroovyStep'){\n" +
                    "  throw new LifeIsToughException('Room is too cold')\n" +
                    "}"
                ));
                b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("Reported=Room is too cold",b);
            }
        });
    }

    /**
     * Tests the shape of the flow graph.
     *
     * <p>
     * See "docs/step-in-groovy.md" for the expected flow graph and its rationale.
     */
    @Test
    public void flowGraph() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                        "helloWorldGroovy('Duke') {\n" +
                            "echo 'mid point'\n"+
                        "}"));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));

                FlowGraphWalker w = new FlowGraphWalker(b.getExecution());
                List<String> nodes = new ArrayList<>();
                for (FlowNode n : w) {
                    String s = n.getClass().getSimpleName();
                    if (n instanceof StepNode) {
                        StepNode sn = (StepNode) n;
                        s+=":"+sn.getDescriptor().getFunctionName();
                    }
                    nodes.add(s);
                }

                assertEquals(Arrays.asList(
                    "FlowEndNode",
                    "StepEndNode:helloWorldGroovy",     // end of helloWorldGroovy
                        "StepAtomNode:echo",            // Good byt from helloWorldGroovy
                        "StepAtomNode:echo",            // mid point
                        "StepAtomNode:echo",            // Hello from inside helloWorldGroovy
                    "StepStartNode:helloWorldGroovy",   // invocation into helloWorldGroovy
                    "FlowStartNode"
                ),nodes);
            }
        });
    }
}
