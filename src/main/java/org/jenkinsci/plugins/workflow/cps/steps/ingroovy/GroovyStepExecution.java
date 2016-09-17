package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * {@link StepExecution} to be implemented in Groovy
 *
 * @author Kohsuke Kawaguchi
 * @see "doc/step-in-groovy.md"
 */
public abstract class GroovyStepExecution extends StepExecution {
    /**
     * Captures parameters that invoke {@link GroovyStep}
     */
    private UninstantiatedDescribable step;

    /**
     * Represents the currently executing groovy code that defines the step.
     */
    private BodyExecution execution;

    /*package*/ CpsStepContext context;

    /*package*/ void init(GroovyStep step, CpsStepContext context) {
        this.step = UninstantiatedDescribable.from(step);
        this.context = context;
    }

    @Nonnull
    @Override
    public CpsStepContext getContext() {
        return context;
    }

    /**
     * Obtains the {@link GroovyStep} that captures parameters given to this step.
     */
    public GroovyStep getStep() throws Exception {
        // intentionally returning a fresh instance to prevent subtypes
        // from incorrectly attempting to store values in fields.
        return (GroovyStep)step.instantiate();
    }

    @Override
    public boolean start() throws Exception {
        CpsStepContext cps = getContext();
        CpsThread t = CpsThread.current();

        // TODO: make sure this class is actually CPS transformed

        Closure body = InvokerHelper.getMethodPointer(this, "call");
        if (getStep().getDescriptor().takesImplicitBlockArgument()) {
            if (body.getMaximumNumberOfParameters()==0)
                throw new IllegalArgumentException(getClass().getName()+" claims to take the body block, but its call method takes no argument.");
            body = body.curry(new Body());
        }

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

    /**
     * Attempt to resolve a method call as a step invocation like Pipeline Script.
     */
    public Object methodMissing(String name, Object args) throws IOException {
        return getDelegate().invokeMethod(name,args);
    }

    /**
     * Attempt to resolve a property access as a step invocation like Pipeline Script.
     */
    public Object propertyMissing(String name) throws IOException {
        return getDelegate().getProperty(name);
    }

    /**
     * Delegate that implements the method/property resolution of Pipeline Script.
     */
    private GroovyObject getDelegate() throws IOException {
        // this needs to happen inside CPS VM to work correctly, hence no caching
        return new CpsScript() {
            @Override
            public Object run() {
                throw new AssertionError();
            }
        };
    }

    /**
     * Passed to the user-written 'call' method in Groovy as 'body'.
     * When invoked, executes the body block passed to the step.
     */
    @SuppressFBWarnings("SE_INNER_CLASS")
    private final class Body extends Closure implements Serializable {
        public Body() {
            super(null);
        }

        @Override
        public Object call() {
            // expected to throw CpsCallableInvocation
            return getContext().newBodyInvoker().getBody().call();
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}

