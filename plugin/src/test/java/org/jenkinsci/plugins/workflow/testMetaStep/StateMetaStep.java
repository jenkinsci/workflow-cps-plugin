package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.TaskListener;
import java.util.Set;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.workflow.cps.DSLTest;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecutions;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Meta-step that executes {@link State}
 *
 * @author Kohsuke Kawaguchi
 * @see DSLTest
 */
public class StateMetaStep extends Step {

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

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return StepExecutions.synchronousNonBlockingVoid(context, c -> {
            TaskListener listener = c.get(TaskListener.class);
            if (moderate) {
                listener.getLogger()
                        .println("Introducing "
                                + SymbolLookup.getSymbolValue(state).iterator().next());
            }
            state.sayHello(listener);
        });
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "state";
        }

        @Override
        public boolean isMetaStep() {
            return true;
        }

        @Override
        public String getDisplayName() {
            return "Greeting from a state";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class);
        }
    }
}
