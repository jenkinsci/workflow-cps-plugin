package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.TaskListener;
import java.util.Set;
import org.jenkinsci.plugins.workflow.cps.DSLTest;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecutions;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Andrew Bayer
 * @see DSLTest
 */
public class MonomorphicWithSymbolStep extends Step {

    public final MonomorphicDataWithSymbol data;

    @DataBoundConstructor
    public MonomorphicWithSymbolStep(MonomorphicDataWithSymbol data) {
        this.data = data;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return StepExecutions.synchronousNonBlockingVoid(
                context, c -> c.get(TaskListener.class).getLogger().println(data.getArgs()));
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "monomorphWithSymbolStep";
        }

        @Override
        public String getDisplayName() {
            return "Testing monomorphic single parameter with a symbol.";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class);
        }
    }
}
