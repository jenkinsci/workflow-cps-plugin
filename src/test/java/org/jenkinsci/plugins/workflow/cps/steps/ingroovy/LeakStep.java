package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

public class LeakStep extends GroovyStep {

    @DataBoundConstructor public LeakStep() {}

    @Extension public static class DescriptorImpl extends GroovyStepDescriptor {

        @Override public String getFunctionName() {
            return "leak";
        }

    }

}
