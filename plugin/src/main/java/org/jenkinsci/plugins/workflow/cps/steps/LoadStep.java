package org.jenkinsci.plugins.workflow.cps.steps;

import hudson.Extension;
import hudson.FilePath;
import hudson.model.TaskListener;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Evaluate arbitrary script file.
 *
 * @author Kohsuke Kawaguchi
 */
public class LoadStep extends Step {
    /**
     * Relative path of the script within the current workspace.
     */
    private final String path;

    @DataBoundConstructor
    public LoadStep(String path) {
        this.path = path;
    }

    public String getPath() {
        return path;
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return new LoadStepExecution(this, context);
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "load";
        }

        @Override
        public String getDisplayName() {
            return "Evaluate a Groovy source file into the Pipeline script";
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(FilePath.class, TaskListener.class);
        }
    }
}
