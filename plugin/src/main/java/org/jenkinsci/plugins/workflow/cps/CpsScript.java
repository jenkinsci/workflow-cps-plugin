/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.cps;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.PROGRAM;

import com.cloudbees.groovy.cps.SerializableScript;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import groovy.lang.GroovyShell;
import groovy.lang.MissingPropertyException;
import groovy.lang.Script;
import hudson.model.Queue;
import hudson.model.Run;
import java.io.File;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.DefaultGroovyStaticMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

/**
 * The script of a workflow.
 *
 * Every {@link Script} we load in Pipeline execution derives from this subtype.
 *
 * @author Kohsuke Kawaguchi
 */
@PersistIn(PROGRAM)
public abstract class CpsScript extends SerializableScript {

    private static final Logger LOGGER = Logger.getLogger(CpsScript.class.getName());

    private static final String STEPS_VAR = "steps";

    transient CpsFlowExecution execution;

    /** Default constructor for {@link CpsFlowExecution}. */
    public CpsScript() throws IOException {
        // if the script is instantiated in workflow, automatically set up the contextual
        // 'execution' object. This allows those scripts to invoke workflow steps without
        // any special setup, making it easy to write reusable functions.
        CpsThread c = CpsThread.current();
        if (c != null) {
            execution = c.getExecution();
            $initialize();
        }
    }

    @SuppressWarnings("unchecked") // Binding
    final void $initialize() throws IOException {
        // TODO JENKINS-33353 could make this a GlobalVariable instead
        getBinding().setVariable(STEPS_VAR, new DSL(execution.getOwner()));
    }

    /**
     * We use DSL here to try invoke the step implementation, if there is Step implementation found it's handled or
     * it's an error.
     *
     * <p>
     * sandbox security execution relies on the assumption that CpsScript.invokeMethod() is safe for sandboxed code.
     * That means we cannot let user-written script override this method, hence the final.
     */
    @Override
    public final Object invokeMethod(String name, Object args) {
        // TODO probably better to call super method and only proceed here incase of MissingMethodException:

        // check for user instantiated objects in the script binding
        // that respond to call.
        if (getBinding().hasVariable(name)) {
            Object o = getBinding().getVariable(name);
            if (!InvokerHelper.getMetaClass(o)
                    .respondsTo(o, "call", (Object[]) args)
                    .isEmpty()) {
                try {
                    return InvokerHelper.getMetaClass(o).invokeMethod(o, "call", args);
                } catch (Exception x) {
                    throw new InvokerInvocationException(x);
                }
            }
        }

        // if global variables are defined by that name, try to call it.
        // the 'call' convention comes from Closure
        GlobalVariable v = GlobalVariable.byName(name, $buildNoException());
        if (v != null) {
            try {
                Object o = v.getValue(this);
                return InvokerHelper.getMetaClass(o).invokeMethod(o, "call", args);
            } catch (Exception x) {
                throw new InvokerInvocationException(x);
            }
        }

        // otherwise try Step impls.
        DSL dsl = (DSL) getBinding().getVariable(STEPS_VAR);
        return dsl.invokeMethod(name, args);
    }

    @Override
    public Object getProperty(String property) {
        try {
            return super.getProperty(property);
        } catch (MissingPropertyException mpe) {
            // cf. CpsWhitelist.permitsMethod
            Run<?, ?> b = $buildNoException();
            GlobalVariable v = GlobalVariable.byName(property, b);
            if (v != null) {
                try {
                    try {
                        return v.getValue(this);
                    } catch (RuntimeException | Error x) {
                        // This method ends up throwing something (original
                        // or changed exception, depending on situation).
                        // Here we anticipate a MethodTooLargeException
                        // (or traces of its message stack), possibly
                        // wrapped into further exception, for actionable
                        // logging in the job.
                        throw CpsFlowExecution.reportSuspectedMethodTooLarge(x);
                    }
                } catch (Throwable x) {
                    throw new InvokerInvocationException(x);
                }
            }
            if (b != null) {
                try {
                    String value = EnvActionImpl.forRun(b).getProperty(property);
                    if (value != null) {
                        return value;
                    }
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
            }
            throw mpe;
        }
    }

    public @CheckForNull Run<?, ?> $build() throws IOException {
        FlowExecutionOwner owner = execution.getOwner();
        Queue.Executable qe = owner.getExecutable();
        if (qe instanceof Run) {
            return (Run) qe;
        } else {
            return null;
        }
    }

    public @CheckForNull Run<?, ?> $buildNoException() {
        if (execution == null) {
            // Still inside the WorkflowScript constructor, e.g. because getProperty is being called from a @Field.
            // In such cases we do not expect to be able to use library variables, so just skip it.
            return null;
        }
        try {
            return $build();
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
            return null;
        }
    }

    @Override
    public Object evaluate(String script) throws CompilationFailedException {
        // this might throw the magic CpsCallableInvocation to execute the script asynchronously
        return $getShell().evaluate(script);
    }

    @Override
    public Object evaluate(File file) throws CompilationFailedException, IOException {
        return $getShell().evaluate(file);
    }

    @Override
    public void run(File file, String[] arguments) throws CompilationFailedException, IOException {
        // GroovyShell.run has a bunch of weird cases related to JUnit and other stuff that we cannot safely support
        // without a lot of extra work, so we just approximate its behavior. Regardless, I assume that this method is
        // essentially unused since it takes a File and it is not allowed by CpsWhitelist (unlike evaluate).
        $getShell().getContext().setProperty("args", arguments);
        evaluate(file);
    }

    /**
     * Obtains the Groovy compiler to be used for compiling user script
     * in the CPS-transformed and sandboxed manner.
     */
    private GroovyShell $getShell() {
        return CpsThreadGroup.current().getExecution().getShell();
    }

    protected Object readResolve() {
        execution = CpsFlowExecution.PROGRAM_STATE_SERIALIZATION.get();
        assert execution != null;
        return this;
    }

    @Override
    public void println() {
        invokeMethod("echo", "");
    }

    @Override
    public void print(Object value) {
        // TODO: handling 'print' correctly requires collapsing multiple adjacent print calls into one Step.
        println(value);
    }

    @Override
    public void println(Object value) {
        invokeMethod("echo", String.valueOf(value));
    }

    @Override
    public void printf(String format, Object value) {
        print(DefaultGroovyMethods.sprintf(this /*not actually used*/, format, value));
    }

    @Override
    public void printf(String format, Object[] values) {
        print(DefaultGroovyMethods.sprintf(this /*not actually used*/, format, values));
    }

    /**
     * Effectively overrides {@link DefaultGroovyStaticMethods#sleep(Object, long)}
     * so that {@code SleepStep} works even in the bare form {@code sleep 5}.
     *
     * @see CpsClosure2#sleep(long)
     */
    @Whitelisted
    public Object sleep(long arg) {
        return invokeMethod("sleep", arg);
    }

    private static final long serialVersionUID = 1L;
}
