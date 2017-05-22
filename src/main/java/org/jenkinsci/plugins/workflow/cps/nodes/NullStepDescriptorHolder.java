package org.jenkinsci.plugins.workflow.cps.nodes;


import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import java.util.Set;

public class NullStepDescriptorHolder extends Step {
    public static final NullStepDescriptor nulllDescriptor = new NullStepDescriptor();

    @Override
    public StepExecution start(StepContext context) throws Exception {
        return null;
    }

    //do not use annoation @extension since we don't want to expose it
    public static class NullStepDescriptor extends StepDescriptor {
        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return null;
        }

        @Override
        public String getFunctionName() {
            return null;
        }
    }
}
