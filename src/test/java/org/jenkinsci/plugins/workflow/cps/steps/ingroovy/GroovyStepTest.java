package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import hudson.model.queue.QueueTaskFuture;
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
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
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import org.codehaus.groovy.transform.ASTTransformationVisitor;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.MemoryAssert;

/**
 * @author Kohsuke Kawaguchi
 * @see "doc/step-in-groovy.md"
 */
public class GroovyStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
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

    @Test
    public void security() throws Exception {
        story.addStep(new Statement() {
            private WorkflowJob p;

            @Override
            public void evaluate() throws Throwable {
                p = story.j.createProject(WorkflowJob.class, "demo");

                // Groovy step should have access to the money in the vault
                p.setDefinition(new CpsFlowDefinition(
                        "assert bankTeller()==2000",
                        true));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));

                // but no direct access allowed
                String className = BankTellerStep.class.getName()+"Execution";

                assertRejection(new RejectedAccessException("new", className),
                        "new " + className + "()");

                assertRejection(new RejectedAccessException("staticField", className+" moneyInVault"),
                        className + ".moneyInVault");
            }

            /**
             * Runs the script and make sure it gets rejected as specified.
             */
            private void assertRejection(RejectedAccessException expected, String script) throws Exception {
                p.setDefinition(new CpsFlowDefinition(script,true));
                WorkflowRun b = p.scheduleBuild2(0).get();
                story.j.assertBuildStatus(FAILURE, b);
                story.j.assertLogContains(expected.getMessage(), b);
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
     * See {@code ComplexStepExecution}.
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

    @Test public void accessToGroovyDefinedGlobalVariable() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("globalVar.field = 'set'; usesGlobalVar()", true));
                story.j.assertLogContains("field is currently set and environment variable is defined", story.j.buildAndAssertSuccess(p));
            }
        });
    }

    private static final List<WeakReference<ClassLoader>> LOADERS = new ArrayList<>();
    public static void register(Object o) {
        LOADERS.add(new WeakReference<>(o.getClass().getClassLoader()));
    }
    @Test public void loaderReleased() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(GroovyStepTest.class.getName() + ".register(this); leak()", false));
                story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                assertFalse(LOADERS.isEmpty());
                try { // For Jenkins/Groovy 1. Cf. CpsFlowExecutionTest.loaderReleased.
                    Field f = ASTTransformationVisitor.class.getDeclaredField("compUnit");
                    f.setAccessible(true);
                    f.set(null, null);
                } catch (NoSuchFieldException e) {}
                for (WeakReference<ClassLoader> loaderRef : LOADERS) {
                    MemoryAssert.assertGC(loaderRef);
                }
            }
        });
    }

}
