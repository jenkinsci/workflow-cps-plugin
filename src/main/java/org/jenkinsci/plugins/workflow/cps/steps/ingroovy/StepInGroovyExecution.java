package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import groovy.lang.Closure;
import groovy.lang.GroovyCodeSource;
import groovy.lang.Script;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.steps.ingroovy.StepInGroovy.StepDescriptorInGroovy;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

import static java.awt.SystemColor.text;

/**
 * Persisted part of the program state that captures executing {@link StepInGroovy}.
 *
 * @see StepInGroovy
 * @see 'doc/step-in-groovy.md'
 * @author Kohsuke Kawaguchi
 */
public class StepInGroovyExecution extends StepExecution {
    /**
     * Arguments.
     */
    private final Map<String,Object> arguments;
    /**
     * Represents the currently executing groovy code that defines the step.
     */
    private BodyExecution execution;
    /**
     * Back pointer to the descriptor that instantiated this.
     * Only needed until {@link #start()} so not persisting.
     */
    private transient StepDescriptorInGroovy descriptor;

    StepInGroovyExecution(StepDescriptorInGroovy d, Map<String,Object> arguments, StepContext context) {
        super(context);
        this.arguments = arguments;
        this.descriptor = d;
    }

    @Override
    public boolean start() throws Exception {
        CpsStepContext cps = (CpsStepContext) getContext();
        CpsThread t = CpsThread.current();

        List<Parameter> pd = descriptor.getParameters();
        Object[] args = new Object[pd.size()];
        for (int i=0; i<args.length; i++) {
            args[i] = arguments.get(pd.get(i).name);
        }

        // see
        Script impl = (Script)t.getExecution().getTrustedShell().getClassLoader()
                .loadClass(descriptor.getClassName()).newInstance();

        Closure body = InvokerHelper.getMethodPointer(impl, "call").curry(args);

        execution = cps.newBodyInvoker(t.getGroup().export(body))
//                .withStartAction(/*... maybe a marker for the future?*/)
                .withCallback(BodyExecutionCallback.wrap(cps))
                .start();

        return false;
    }

    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {
        execution.cancel(cause);
    }

    private static final long serialVersionUID = 1L;
}
