package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.TaskListener;
import java.io.Serializable;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

public class EchoStringAndDoubleStep extends Step implements Serializable {

    private double number;
    private String string;

    @DataBoundConstructor
    public EchoStringAndDoubleStep(String string) {
        this.string = string;
    }

    public String getString() {
        return string;
    }

    @DataBoundSetter
    public void setNumber(double number) {
        this.number = number;
    }

    public double getNumber() {
        return number;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new EchoStringAndDoubleStepExecution(this, context);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "echoStringAndDouble";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class);
        }
    }

    public static class EchoStringAndDoubleStepExecution extends StepExecution {
        private final EchoStringAndDoubleStep step;

        public EchoStringAndDoubleStepExecution(EchoStringAndDoubleStep s, StepContext context) {
            super(context);
            this.step = s;
        }

        @Override
        public boolean start() throws Exception {
            TaskListener listener = getContext().get(TaskListener.class);

            if (listener != null)
                listener.getLogger().println("String is " + step.getString() + ", number is " + step.getNumber());

            return true;
        }
    }

    private static final long serialVersionUID = 1L;
}
