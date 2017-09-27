package org.jenkinsci.plugins.workflow.cps;

import com.google.common.base.Function;
import com.google.common.collect.Sets;
import groovy.lang.Closure;
import hudson.model.Result;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nonnull;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepNode;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class CpsBodyExecutionTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule jenkins = new JenkinsRule();

    /**
     * When the body of a step is synchronous and explodes, the failure should be recorded and the pipeline job
     * should move on.
     *
     * But instead, this hangs because CpsBodyExecution has a bug in how it handles this case.
     * It tries to launch the body (in this case the 'bodyBlock' method) in a separate CPS thread,
     * and puts the parent CPS thread on hold. Yet when the child CPS thread ends with an exception,
     * it fails to record this result correctly, and it gets into the eternal sleep in which
     * the parent CPS thread expects to be notified of the outcome of the child CPS thread, which
     * never arrives.
     */
    @Test
    public void synchronousExceptionInBody() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("synchronousExceptionInBody()",true));

        WorkflowRun b = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains(EmperorHasNoClothes.class.getName(),b);

        {// assert the shape of FlowNodes
            FlowGraphWalker w = new FlowGraphWalker(b.getExecution());
            List<String> nodes = new ArrayList<>();
            for (FlowNode n : w) {
                String s = n.getClass().getSimpleName();
                if (n instanceof StepNode) {
                    StepNode sn = (StepNode) n;
                    s+=":"+sn.getDescriptor().getFunctionName();
                }
                if (n instanceof StepEndNode) {
                    // this should have recorded a failure
                    ErrorAction e = n.getAction(ErrorAction.class);
                    assertEquals(EmperorHasNoClothes.class,e.getError().getClass());
                }
                nodes.add(s);
            }

            assertEquals(Arrays.asList(
                "FlowEndNode",
                "StepEndNode:synchronousExceptionInBody",   // this for the end of invoking a body
                "StepStartNode:synchronousExceptionInBody", // this for invoking a body
                // this for the 'synchronousExceptionInBody' invocation because Pipeline Engine
                // thinks this step has no children. There's a TODO in DSL.invokeStep about
                // letting steps take over the FlowNode creation that should solve this.
                "StepAtomNode:synchronousExceptionInBody",
                "FlowStartNode"
            ),nodes);
        }
    }

    public static class SynchronousExceptionInBodyStep extends AbstractStepImpl {
        @DataBoundConstructor
        public SynchronousExceptionInBodyStep() {}

        @TestExtension
        public static class DescriptorImpl extends AbstractStepDescriptorImpl {
            public DescriptorImpl() {
                super(Execution.class);
            }

            @Override
            public String getFunctionName() {
                return "synchronousExceptionInBody";
            }
        }

        public static class Execution extends AbstractStepExecutionImpl {
            /**
             * Invoked as a body that induces a synchronous exception
             */
            public void bodyBlock() {
                throw new EmperorHasNoClothes();
            }

            @Override
            public boolean start() throws Exception {
                Closure body = ScriptBytecodeAdapter.getMethodPointer(this, "bodyBlock");
                CpsStepContext cps = (CpsStepContext) getContext();
                CpsThread t = CpsThread.current();
                cps.newBodyInvoker(t.getGroup().export(body))
                        .withCallback(BodyExecutionCallback.wrap(cps))
                        .start();
                return false;
            }
        }

    }

    @Issue("JENKINS-34637")
    @Test public void currentExecutions() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("parallel main: {retainsBody {parallel a: {retainsBody {semaphore 'a'}}, b: {retainsBody {semaphore 'b'}}}}, aside: {semaphore 'c'}", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("a/1", b);
        SemaphoreStep.waitForStart("b/1", b);
        SemaphoreStep.waitForStart("c/1", b);
        final RetainsBodyStep.Execution[] execs = new RetainsBodyStep.Execution[3];
        StepExecution.applyAll(RetainsBodyStep.Execution.class, new Function<RetainsBodyStep.Execution, Void>() {
            @Override public Void apply(RetainsBodyStep.Execution exec) {
                execs[exec.count] = exec;
                return null;
            }
        }).get();
        assertNotNull(execs[0]);
        assertNotNull(execs[1]);
        assertNotNull(execs[2]);
        final Set<SemaphoreStep.Execution> semaphores = new HashSet<>();
        StepExecution.applyAll(SemaphoreStep.Execution.class, new Function<SemaphoreStep.Execution, Void>() {
            @Override public Void apply(SemaphoreStep.Execution exec) {
                if (exec.getStatus().matches("waiting on [ab]/1")) {
                    semaphores.add(exec);
                }
                return null;
            }
        }).get();
        assertThat(semaphores, Matchers.<SemaphoreStep.Execution>iterableWithSize(2));
        Collection<StepExecution> currentExecutions1 = execs[1].body.getCurrentExecutions(); // A or B, does not matter
        assertThat(/* irritatingly, iterableWithSize does not show the collection in its mismatch message */currentExecutions1.toString(),
            currentExecutions1, Matchers.<StepExecution>iterableWithSize(1));
        Collection<StepExecution> currentExecutions2 = execs[2].body.getCurrentExecutions();
        assertThat(currentExecutions2, Matchers.<StepExecution>iterableWithSize(1));
        assertEquals(semaphores, Sets.union(Sets.newLinkedHashSet(currentExecutions1), Sets.newLinkedHashSet(currentExecutions2)));
        assertEquals(semaphores, Sets.newLinkedHashSet(execs[0].body.getCurrentExecutions())); // the top-level one
        execs[0].body.cancel();
        SemaphoreStep.success("c/1", null);
        jenkins.assertBuildStatus(Result.ABORTED, jenkins.waitForCompletion(b));
    }
    public static class RetainsBodyStep extends AbstractStepImpl {
        @DataBoundConstructor public RetainsBodyStep() {}
        @TestExtension("currentExecutions") public static class DescriptorImpl extends AbstractStepDescriptorImpl {
            public DescriptorImpl() {super(Execution.class);}
            @Override public String getFunctionName() {return "retainsBody";}
            @Override public boolean takesImplicitBlockArgument() {return true;}
        }
        public static class Execution extends AbstractStepExecutionImpl {
            static int counter;
            BodyExecution body;
            int count = counter++;
            @Override public boolean start() throws Exception {
                body = getContext().newBodyInvoker().withCallback(BodyExecutionCallback.wrap(getContext())).start();
                return false;
            }
            @Override public void stop(Throwable cause) throws Exception {
                throw new AssertionError("block #" + count + " not supposed to be killed directly", cause);
            }
        }
    }

}