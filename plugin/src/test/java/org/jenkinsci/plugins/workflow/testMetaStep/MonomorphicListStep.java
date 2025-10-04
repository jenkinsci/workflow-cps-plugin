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
public class MonomorphicListStep extends Step {

    public final List<MonomorphicData> data;

    @DataBoundConstructor
    public MonomorphicListStep(List<MonomorphicData> data) {
        this.data = data;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return StepExecutions.synchronousNonBlockingVoid(context, c -> {
            for (MonomorphicData d : data) {
                c.get(TaskListener.class).getLogger().println(d.getArgs());
            }
        });
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "monomorphListStep";
        }

        @Override
        public String getDisplayName() {
            return "Testing monomorphic list single parameter.";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class);
        }
    }
}
