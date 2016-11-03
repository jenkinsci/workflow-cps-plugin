package org.jenkinsci.plugins.workflow.testMetaStep;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by rishi.manidhar on 11/2/16.
 */
public class DisplaynameWithEscapeCharState extends AbstractStepImpl {
    public final DisplaynameEscapeCharData data;

    @DataBoundConstructor
    public DisplaynameWithEscapeCharState(DisplaynameEscapeCharData data) {
        this.data = data;
    }

    private static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        @Inject
        private transient DisplaynameWithEscapeCharState step;
        @StepContextParameter
        private transient TaskListener listener;

        @Override protected Void run() throws Exception {
            listener.getLogger().println(step.data.getArgs());
            return null;
        }

        private static final long serialVersionUID = 1L;

    }

    @Extension
    public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override public String getFunctionName() {
            return "displaynameWithEscapeCharState";
        }

        @Override public String getDisplayName() {
            return "Testing 'escape characters' are added when needed.";
        }
    }
}
