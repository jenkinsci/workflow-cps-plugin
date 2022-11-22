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

/**
 * @author Andrew Bayer
 * @see DSLTest
 */
public class MonomorphicWithSymbolStep extends AbstractStepImpl {

    public final MonomorphicDataWithSymbol data;

    @DataBoundConstructor
    public MonomorphicWithSymbolStep(MonomorphicDataWithSymbol data) {
        this.data = data;
    }

    private static final class Execution extends AbstractSynchronousNonBlockingStepExecution<Void> {
        @Inject
        private transient MonomorphicWithSymbolStep step;
        @StepContextParameter
        private transient TaskListener listener;

        @Override protected Void run() throws Exception {
            listener.getLogger().println(step.data.getArgs());
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
            return "monomorphWithSymbolStep";
        }

        @Override public String getDisplayName() {
            return "Testing monomorphic single parameter with a symbol.";
        }
    }


}
