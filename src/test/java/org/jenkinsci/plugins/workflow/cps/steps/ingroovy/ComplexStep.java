package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class ComplexStep extends GroovyStep {
    private final List<Integer> numbers;

    @DataBoundConstructor
    public ComplexStep(List<Integer> numbers) {
        this.numbers = numbers;
    }

    public List<Integer> getNumbers() {
        return numbers;
    }

    @Extension
    public static class DescriptorImpl extends GroovyStepDescriptor {
        @Override
        public String getFunctionName() {
            return "complex";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }
}
