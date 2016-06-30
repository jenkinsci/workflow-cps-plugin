package org.jenkinsci.plugins.workflow.testMetaStep;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
 */
public class StateMetaStep extends AbstractStepImpl {

    public final State state;

    @DataBoundConstructor
    public StateMetaStep(State state) {
        this.state = state;
    }

    private static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        @Inject
        private transient StateMetaStep step;
        @StepContextParameter
        private transient TaskListener listener;

        @Override protected Void run() throws Exception {
            step.state.sayHello(listener);
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
            return "state";
        }

        @Override public String getDisplayName() {
            return "Greeting from a state";
        }
    }
}
