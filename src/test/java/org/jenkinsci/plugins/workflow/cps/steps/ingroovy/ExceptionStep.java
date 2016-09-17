package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 * @see GroovyStepTest#exception()
 */
public class ExceptionStep extends GroovyStep {
    private String mode;

    @DataBoundConstructor
    public ExceptionStep(String mode) {
        this.mode = mode;
    }

    public String getMode() {
        return mode;
    }

    @Extension
    public static class DescriptorImpl extends GroovyStepDescriptor {
        @Override
        public String getFunctionName() {
            return "exception";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }
}
