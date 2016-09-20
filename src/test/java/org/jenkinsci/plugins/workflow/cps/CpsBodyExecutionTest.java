package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.Closure;
import hudson.model.Result;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
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
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.junit.Test;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Kohsuke Kawaguchi
 */
public class CpsBodyExecutionTest extends AbstractCpsFlowTest {
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

            @Override
            public void stop(@Nonnull Throwable cause) throws Exception {
                throw new UnsupportedOperationException();
            }
        }

    }

}