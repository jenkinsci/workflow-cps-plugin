package org.jenkinsci.plugins.workflow.testMetaStep.chemical;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.jenkinsci.plugins.workflow.testMetaStep.StateMetaStep;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Prefer this over {@link StateMetaStep} via ordinal
 *
 * @author Kohsuke Kawaguchi
 */
public class DetectionMetaStep extends AbstractStepImpl {

    public final Chemical compound;

    @DataBoundConstructor
    public DetectionMetaStep(Chemical compound) {
        this.compound = compound;
    }

    private static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        @Inject
        private transient DetectionMetaStep step;
        @StepContextParameter
        private transient TaskListener listener;

        @Override protected Void run() throws Exception {
            listener.getLogger().println("Detecting "+step.compound.getClass().getName());
            return null;
        }

        private static final long serialVersionUID = 1L;

    }

    @Extension(ordinal=100)
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "detect";
        }

        @Override
        public boolean isMetaStep() {
            return true;
        }

        @Override public String getDisplayName() {
            return "Detect a chemical compound";
        }
    }
}
