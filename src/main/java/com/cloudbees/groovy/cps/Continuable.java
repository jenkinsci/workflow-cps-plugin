package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.Conclusion;
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.CpsFunction;
import com.cloudbees.groovy.cps.impl.FunctionCallEnv;
import com.cloudbees.groovy.cps.impl.YieldBlock;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Mutable representation of the program. This is the primary API of the groovy-cps library to the outside.
 *
 * @author Kohsuke Kawaguchi
 */
public class Continuable implements Serializable {
    /**
     * Represents the remainder of the program to execute.
     */
    private Resumable program;

    public Continuable(Resumable program) {
        this.program = program;
    }

    public Continuable(Next program) {
        this(program.asResumable());
    }

    /**
     * Creates a {@link Continuable} that executes the block of code.
     */
    public Continuable(Block block) {
        this(new Next(block,
                new FunctionCallEnv(null,null,Continuation.HALT),
                Continuation.HALT));
    }

    /**
     * Takes a {@link Script} compiled from CPS-transforming {@link GroovyShell} and
     * wraps that into a {@link Continuable}.
     */
    public Continuable(Script cpsTransformedScript) {
        this(wrap(cpsTransformedScript));
    }

    private static Next wrap(Script s) {
        try {
            Method m = s.getClass().getMethod("run");
            if (!m.isAnnotationPresent(WorkflowTransformed.class))
                throw new IllegalArgumentException(s+" is not CPS-transformed");
            s.run();
            throw new AssertionError("I'm confused if Script is CPS-transformed or not!");
        } catch (CpsCallableInvocation e) {
            return e.invoke(null, Continuation.HALT);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Creates a shallow copy of {@link Continuable}. The copy shares
     * all the local variables of the original {@link Continuable}, and
     * point to the exact same point of the program.
     */
    public Continuable fork() {
        return new Continuable(program);
    }

    /**
     * Starts/resumes this program until it suspends the next time.
     *
     * @throws InvocationTargetException
     *      if the program threw an exception that it didn't handle by itself.
     */
    public Object run(Object arg) throws InvocationTargetException {
        return run0(new Conclusion(arg,null));
    }

    public Object runByThrow(Throwable arg) throws InvocationTargetException {
        return run0(new Conclusion(null,arg));
    }

    private Object run0(Conclusion arg) throws InvocationTargetException {
        Next n = program.receive(arg).run();
        // when yielding, we resume from the continuation so that we can pass in the value.
        // see Next#yield
        program = n.resumable;
        return n.yield.eval();
    }

    /**
     * Checks if this {@link Continuable} is pointing at the end of the program which cannot
     * be resumed.
     *
     * If this method returns false, it is illegal to call {@link #run(Object)}
     */
    public boolean isResumable() {
        return program!=null;
    }

    /**
     * Called from within CPS transformed program to suspends the execution.
     *
     * <p>
     * When this method is called, the control goes back to
     * the caller of {@link #run(Object)}, which returns with the argument given to this method.
     *
     * <p>
     * When the continuable is resumed via {@link #run(Object)} later, the argument to the run method
     * will become the return value from this method to the CPS-transformed program.
     */
    public static Object suspend(final Object v) {
        throw new CpsCallableInvocation(new CpsFunction(Arrays.asList("v"), new YieldBlock(v)),null,v);
    }

    private static final long serialVersionUID = 1L;
}
