package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

/**
 * @author Kohsuke Kawaguchi
 * @see "doc/step-in-groovy.md"
 */
public abstract class GroovyStep extends Step {
    @Override
    public GroovyStepDescriptor getDescriptor() {
        return (GroovyStepDescriptor) super.getDescriptor();
    }

    @Override
    public StepExecution start(StepContext context) throws Exception {
        CpsThread t = CpsThread.current();

        GroovyStepExecution impl = (GroovyStepExecution)t.getExecution().getTrustedShell().getClassLoader()
                .loadClass(getDescriptor().getExecutionClassName()).newInstance();
        impl.init(this,(CpsStepContext)context);

        return impl;
    }
}
