package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTest;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Step that collides with a describable
 *
 * @author Kohsuke Kawaguchi
 * @see Oregon
 * @see SnippetizerTest#collisionWithStep()
 */
public class OrStep extends AbstractStepImpl {

    @DataBoundConstructor
    public OrStep() {}

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {

        public DescriptorImpl() {
            super(Execution.class);
        }

        @Override
        public String getFunctionName() {
            return "or";
        }

        @Override
        public String getDisplayName() {
            return "Oregon? or logical or?";
        }
    }

    public static class Execution extends AbstractSynchronousStepExecution<Void> {
        @Override
        protected Void run() throws Exception {
            return null;
        }

        private static final long serialVersionUID = 1L;
    }
}
