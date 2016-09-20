package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.Closure;
import groovy.lang.GroovyObject;
import groovy.lang.MissingMethodException;
import groovy.lang.MissingPropertyException;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.CpsStepContext;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.steps.BodyExecution;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.StepExecution;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;

/**
 * {@link StepExecution} to be implemented in Groovy
 *
 * @author Kohsuke Kawaguchi
 * @see "doc/step-in-groovy.md"
 */
@PersistIn(PROGRAM)
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
        // it's possible that the plugin dev has messed up and put the subtype in src/main/groovy,
        // ... in which case the class will resolve but will not work as expected.

        Closure body = InvokerHelper.getMethodPointer(this, "call");
        if (getStep().getDescriptor().takesImplicitBlockArgument()) {
            if (body.getMaximumNumberOfParameters()==0)
                throw new IllegalArgumentException(getClass().getName()+" claims to take the body block, but its call method takes no argument.");
            body = body.curry(new Body());
        }

        execution = cps.newBodyInvoker(t.getGroup().export(body))
//                .withStartAction(/*... maybe a marker for the future?*/)
                .withCallback(BodyExecutionCallback.wrap(cps))
                .withoutBlockNode()
                .start();

        return false;
    }

    @Override
    public void stop(@Nonnull Throwable cause) throws Exception {
        if (execution!=null)
            execution.cancel(cause);
    }

    /**
     * Attempt to resolve a method call as a step invocation like Pipeline Script.
     *
     * See http://groovy-lang.org/metaprogramming.html#_methodmissing
     */
    public Object methodMissing(String name, Object args) throws IOException {
        GroovyObject d = getDelegate();
        if (d!=null)
            return d.invokeMethod(name,args);
        else
            throw new MissingMethodException(name,getClass(),unpack(args));
    }

    private Object[] unpack(Object args) {
        if (args instanceof Object[])
            return (Object[]) args;
        if (args==null)
            return new Object[0];
        else
            return new Object[]{args};
    }

    /**
     * Attempt to resolve a property access as a step invocation like Pipeline Script.
     *
     * See http://groovy-lang.org/metaprogramming.html#_propertymissing
     */
    public Object propertyMissing(String name) throws IOException {
        GroovyObject d = getDelegate();
        if (d!=null)
            return d.getProperty(name);
        else
            throw new MissingPropertyException(name,getClass());
    }

    /**
     * Delegate that implements the method/property resolution of Pipeline Script.
     *
     * <p>
     * This needs to happen inside CPS VM to work correctly, hence no caching.
     *
     * @return null
     *      if this method is invoked (incorrectly) outside the CPS VM thread.
     */
    private @CheckForNull  GroovyObject getDelegate() throws IOException {
        if (CpsThread.current()==null)
            return null;    // invocation outside CPS VM thread
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
    @PersistIn(PROGRAM)
    private final class Body extends Closure {
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

