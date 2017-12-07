package org.jenkinsci.plugins.workflow.cps.steps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.model.Result;
import org.jenkinsci.plugins.workflow.actions.FlowNodeStatusAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.Set;

public final class SetStatusStep extends Step {
    private final String result;

    @DataBoundConstructor
    public SetStatusStep(@Nonnull String result) {
        this.result = result;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return new Execution(result, context);
    }

    public static final class Execution extends SynchronousStepExecution<Void> {

        private static final long serialVersionUID = 1L;

        @SuppressFBWarnings(value="SE_TRANSIENT_FIELD_NOT_RESTORED", justification="Only used when starting.")
        private transient final String result;

        Execution(String result, StepContext context) {
            super(context);
            this.result = result;
        }

        @Override protected Void run() throws Exception {
            FlowNode node = getContext().get(FlowNode.class);
            if (node != null) {
                node.replaceAction(new FlowNodeStatusAction(Result.fromString(result)));
            }
            return null;
        }
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override public String getFunctionName() {
            return "status";
        }

        @Override public String getDisplayName() {
            return "Set stage or parallel branch status";
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(FlowNode.class);
        }

    }

}
