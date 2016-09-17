package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

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
    private transient GroovyStep step;

    /**
     * Represents the currently executing groovy code that defines the step.
     */
    private BodyExecution execution;

    /*package*/ CpsStepContext context;

    /*package*/ void init(GroovyStep step, CpsStepContext context) {
        this.step = step;
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
    public GroovyStep getStep() {
        return step;
    }

    @Override
    public boolean start() throws Exception {
        CpsStepContext cps = getContext();
        CpsThread t = CpsThread.current();

        // TODO: make sure this class is actually CPS transformed

        // TODO: dealing with body
        Closure body = InvokerHelper.getMethodPointer(this, "call");

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
     * Persist {@link #step} in a form that's serializable.
     */
    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.defaultWriteObject();
        oos.writeObject(UninstantiatedDescribable.from(step));
    }

    /**
     * Pair up with {@link #writeObject(ObjectOutputStream)} to restore {@link #step}
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        UninstantiatedDescribable ud = (UninstantiatedDescribable)ois.readObject();
        try {
            step = (GroovyStep)ud.instantiate();
        } catch (Exception e) {
            throw new IOException("Failed to instantiate "+ud,e);
        }
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

    private static final long serialVersionUID = 1L;
}

