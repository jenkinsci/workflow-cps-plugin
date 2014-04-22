package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.FunctionCallEnv;
import com.cloudbees.groovy.cps.impl.SuspendBlock;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Mutable representation of the program. This is the primary API of the groovy-cps library to the outside.
 *
 * @author Kohsuke Kawaguchi
 */
public class Continuable implements Serializable {
    /**
     * When the program resumes with a value (in particular an exception thrown), what environment
     * do we evaluate that in?
     */
    private Env e;

    /**
     * Represents the remainder of the program to execute.
     */
    private Continuation k;

    public Continuable(Continuable src) {
        this.e = src.e;
        this.k = src.k;
    }

    public Continuable(Next n) {
        this.e = n.e;
        this.k = n;
    }

    /**
     * Creates a {@link Continuable} that executes the block of code in a fresh empty environment.
     */
    public Continuable(Block block) {
        this(block, new FunctionCallEnv(null,null,Continuation.HALT));
    }

    /**
     * Creates a {link Continuable} that executes the block in the specified environment.
     */
    public Continuable(Block block, Env e) {
        this.e = e;
        this.k = new Next(block,e,Continuation.HALT);
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
        return new Continuable(this);
    }

    /**
     * Starts/resumes this program until it suspends the next time.
     *
     * @throws InvocationTargetException
     *      if the program threw an exception that it didn't handle by itself.
     */
    public Object run(Object arg) throws InvocationTargetException {
        return run0(new Outcome(arg,null)).wrapReplay();
    }

    public Object runByThrow(Throwable arg) throws InvocationTargetException {
        return run0(new Outcome(null,arg)).wrapReplay();
    }

    /**
     * Resumes this program by either returning the value from {@link Continuable#suspend(Object)} or
     * throwing an exception
     */
    public Outcome run0(Outcome cn) {
        Next n = cn.resumeFrom(e,k);

        n = n.run();

        e = n.e;
        k = n.k;

        return n.yield;
    }

    /**
     * Checks if this {@link Continuable} is pointing at the end of the program which cannot
     * be resumed.
     */
    public boolean isResumable() {
        return k!=Continuation.HALT || e!=null;
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
        throw new CpsCallableInvocation(SuspendBlock.SUSPEND,null,v);
    }

    private static final long serialVersionUID = 1L;
}
