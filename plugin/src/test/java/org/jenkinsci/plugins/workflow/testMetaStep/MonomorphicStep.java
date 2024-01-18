package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.DSLTest;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecutions;

/**
 * @author Andrew Bayer
 * @see DSLTest
 */
public class MonomorphicStep extends Step {

    public final MonomorphicData data;

    @DataBoundConstructor
    public MonomorphicStep(MonomorphicData data) {
        this.data = data;
    }

    @Override public StepExecution start(StepContext context) throws Exception {
        return StepExecutions.synchronousNonBlockingVoid(context, c -> c.get(TaskListener.class).getLogger().println(data.getArgs()));
    }

    @Extension
    public static final class DescriptorImpl extends StepDescriptor  {

        @Override public String getFunctionName() {
            return "monomorphStep";
        }

        @Override public String getDisplayName() {
            return "Testing monomorphic single parameter.";
        }


        @Override
        public String argumentsToString(Map<String, Object> map) {
            if (map.get("data") instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String,String> data = (Map<String,String>)(map.get("data"));
                return data.get("firstArg")+","+data.get("secondArg");
            }
            return null;
        }

        @Override public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(TaskListener.class);
        }

    }

}
