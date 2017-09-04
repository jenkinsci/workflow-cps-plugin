package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.Result;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

public class EchoResultStep extends Step implements Serializable {

    private Result result;

    @DataBoundConstructor
    public EchoResultStep(Result result) {
        this.result = result;
    }

    public Result getResult() {
        return result;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new EchoResultStepExecution(this, context);
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {
        @Override
        public String getFunctionName() {
            return "echoResult";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Collections.singleton(TaskListener.class);
        }
    }

    public static class EchoResultStepExecution extends StepExecution {
        private final EchoResultStep step;

        public EchoResultStepExecution(EchoResultStep s, StepContext context) {
            super(context);
            this.step = s;
        }

        @Override
        public boolean start() throws Exception {
            TaskListener listener = getContext().get(TaskListener.class);

            if (listener != null)
                listener.getLogger().println("Result is " + step.getResult());

            return true;
        }
    }

    private static final long serialVersionUID = 1L;

}
