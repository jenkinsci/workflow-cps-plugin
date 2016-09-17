package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Step written in Groovy that invokes the body arbitrary amount of times.
 *
 * @author Kohsuke Kawaguchi
 */
public class LoopStep extends GroovyStep {
    private int count;
    private boolean parallel;

    @DataBoundConstructor
    public LoopStep(int count) {
        this.count = count;
    }

    public boolean isParallel() {
        return parallel;
    }

    @DataBoundSetter
    public void setParallel(boolean parallel) {
        this.parallel = parallel;
    }

    public int getCount() {
        return count;
    }

    @Extension
    public static class DescriptorImpl extends GroovyStepDescriptor {
        @Override
        public String getFunctionName() {
            return "loop";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }
}
