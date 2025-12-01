package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.TaskListener;
import java.util.List;
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
public class MonomorphicListWithSymbolStep extends Step {

    public final List<MonomorphicDataWithSymbol> data;

    @DataBoundConstructor
    public MonomorphicListWithSymbolStep(List<MonomorphicDataWithSymbol> data) {
        this.data = data;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return StepExecutions.synchronousNonBlockingVoid(context, c -> {
            for (MonomorphicDataWithSymbol d : data) {
                c.get(TaskListener.class).getLogger().println(d.getArgs());
            }
        });
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "monomorphListSymbolStep";
        }

        @Override
        public String getDisplayName() {
            return "Testing monomorphic list single parameter with symbol.";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class);
        }
    }
}
