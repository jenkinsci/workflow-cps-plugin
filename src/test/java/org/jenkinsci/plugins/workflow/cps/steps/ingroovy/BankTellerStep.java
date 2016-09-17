package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class BankTellerStep extends GroovyStep {
    @DataBoundConstructor
    public BankTellerStep() {
    }

    @Extension
    public static class DescriptorImpl extends GroovyStepDescriptor {
        @Override
        public String getFunctionName() {
            return "bankTeller";
        }
    }
}
