package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import com.cloudbees.plugins.credentials.Credentials;
import hudson.Extension;
import hudson.model.ParameterDefinition;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class ComplexStep extends GroovyStep {
    private final List<Integer> numbers;
    private ParameterDefinition param;

    @DataBoundConstructor
    public ComplexStep(List<Integer> numbers) {
        this.numbers = numbers;
    }

    public List<Integer> getNumbers() {
        return numbers;
    }

    public ParameterDefinition getParam() {
        return param;
    }

    @DataBoundSetter
    public void setParam(ParameterDefinition param) {
        this.param = param;
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
