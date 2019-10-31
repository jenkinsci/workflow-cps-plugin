package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Test class to make sure step descriptions with special characters are escaped properly.
 */
public class DisplaynameWithEscapeCharState extends AbstractStepImpl {

    @DataBoundConstructor
    public DisplaynameWithEscapeCharState() {}

    private static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        @Override protected Void run() throws Exception {
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
