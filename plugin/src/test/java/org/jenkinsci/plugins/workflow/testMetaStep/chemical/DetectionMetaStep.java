package org.jenkinsci.plugins.workflow.testMetaStep.chemical;

import hudson.Extension;
import hudson.model.TaskListener;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecutions;
import org.jenkinsci.plugins.workflow.testMetaStep.StateMetaStep;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Prefer this over {@link StateMetaStep} via ordinal
 *
 * @author Kohsuke Kawaguchi
 */
public class DetectionMetaStep extends Step {

    public final Chemical compound;

    @DataBoundConstructor
    public DetectionMetaStep(Chemical compound) {
        this.compound = compound;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return StepExecutions.synchronousNonBlockingVoid(context, c -> c.get(TaskListener.class)
                .getLogger()
                .println("Detecting " + compound.getClass().getName()));
    }

    @Extension(ordinal = 100)
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "detect";
        }

        @Override
        public boolean isMetaStep() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Detect a chemical compound";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class);
        }
    }
}
