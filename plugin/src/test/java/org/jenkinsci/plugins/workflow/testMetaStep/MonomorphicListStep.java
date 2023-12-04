package org.jenkinsci.plugins.workflow.testMetaStep;

import com.google.inject.Inject;
import hudson.Extension;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.DSLTest;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;

import java.util.List;

/**
 * @author Andrew Bayer
 * @see DSLTest
 */
public class MonomorphicListStep extends AbstractStepImpl {

    public final List<MonomorphicData> data;

    @DataBoundConstructor
    public MonomorphicListStep(List<MonomorphicData> data) {
        this.data = data;
    }

    private static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        @Inject
        private transient MonomorphicListStep step;
        @StepContextParameter
        private transient TaskListener listener;

        @Override protected Void run() throws Exception {
            for (MonomorphicData d : step.data) {
                listener.getLogger().println(d.getArgs());
            }
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
            return "monomorphListStep";
        }

        @Override public String getDisplayName() {
            return "Testing monomorphic list single parameter.";
        }
    }


}
