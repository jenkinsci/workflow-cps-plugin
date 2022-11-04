package org.jenkinsci.plugins.workflow.testMetaStep;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.workflow.cps.DSLTest;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Meta-step that executes {@link State}
 *
 * @author Kohsuke Kawaguchi
 * @see DSLTest
 */
public class StateMetaStep extends AbstractStepImpl {

    public final State state;
    private boolean moderate;

    @DataBoundConstructor
    public StateMetaStep(State state) {
        this.state = state;
    }

    public boolean getModerate() {
        return moderate;
    }

    @DataBoundSetter
    public void setModerate(boolean m) {
        this.moderate = m;
    }

    private static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        @Inject
        private transient StateMetaStep step;
        @StepContextParameter
        private transient TaskListener listener;

        @Override protected Void run() throws Exception {
            if (step.moderate) {
                listener.getLogger().println("Introducing "+ SymbolLookup.getSymbolValue(step.state).iterator().next());
            }
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

        @Override
        public boolean isMetaStep() {
            return true;
        }

        @Override public String getDisplayName() {
            return "Greeting from a state";
        }
    }
}
