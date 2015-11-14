package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.ConstantBlock;
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.SourceLocation;
import com.cloudbees.groovy.cps.impl.SuspendBlock;
import com.cloudbees.groovy.cps.impl.ThrowBlock;
import com.cloudbees.groovy.cps.sandbox.Invoker;
import com.google.common.base.Function;
import groovy.lang.GroovyShell;
import groovy.lang.Script;

import java.io.PrintWriter;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static com.cloudbees.groovy.cps.impl.SourceLocation.UNKNOWN;

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

    private volatile Throwable interrupt;

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
        this(block, Envs.empty());
    }

    /**
     * Creates a {@link Continuable} that executes the block in the specified environment.
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
        this(cpsTransformedScript,null);
    }

    /**
     * Takes a {@link Script} compiled from CPS-transforming {@link GroovyShell} and
     * wraps that into a {@link Continuable}, in the context of the given {@link Env}.
     *
     * The added 'env' parameter can be used to control the execution flow in case
     * of exceptions, and/or providing custom {@link Invoker}
     */
    public Continuable(Script cpsTransformedScript, Env env) {
        this(cpsTransformedScript,env, Continuation.HALT);
    }

    /**
     * Takes a {@link Script} compiled from CPS-transforming {@link GroovyShell} and
     * wraps that into a {@link Continuable}.
     *
     * The added 'k' parameter can be used to pass the control to somewhere else
     * when the script has finished executing.
     */
    public Continuable(Script cpsTransformedScript, Env env, Continuation k) {
        this(wrap(cpsTransformedScript,env,k));
    }

    private static Next wrap(Script s, Env env, Continuation k) {
        try {
            Method m = s.getClass().getMethod("run");
            if (!m.isAnnotationPresent(WorkflowTransformed.class))
                throw new IllegalArgumentException(s+" is not CPS-transformed");
            s.run();
            throw new AssertionError("I'm confused if Script is CPS-transformed or not!");
        } catch (CpsCallableInvocation e) {
            return e.invoke(env, null, k);
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
     * Prints the stack trace into the given writer, much like {@link Throwable#printStackTrace(PrintWriter)}
     */
    public void printStackTrace(PrintWriter s) {
        for (StackTraceElement t : getStackTrace())
            s.println("\tat " + t);
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

        while(n.yield==null) {
            if (interrupt!=null) {
                // TODO: correctly reporting a source location requires every block to have the line number
                n = new Next(new ThrowBlock(UNKNOWN, new ConstantBlock(interrupt), true),n.e,n.k);
                interrupt = null;
            }
            n = n.step();
        }

        e = n.e;
        k = n.k;

        return n.yield;
    }

    /**
     * Sets a super-interrupt.
     *
     * <p>
     * A super interrupt works like {@link Thread#interrupt()} that throws
     * {@link InterruptedException}. It lets other threads interrupt the execution
     * of {@link Continuable} by making it throw an exception.
     *
     * <p>
     * Unlike {@link InterruptedException}, which only gets thrown in specific
     * known locations, such as {@link Object#wait()}, this "super interruption"
     * gets thrown at any point in the execution, even during {@code while(true) ;} kind of loop.
     *
     * <p>
     * The
     */
    public void superInterrupt(Throwable t) {
        this.interrupt = t;
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

    /**
     * Returns the stack trace in the CPS-transformed code that indicates where this {@link Continuable} will resume from.
     * If this object represents a yet-started program, an empty list will be returned.
     */
    public List<StackTraceElement> getStackTrace() {
        List<StackTraceElement> r = new ArrayList<StackTraceElement>();
        if (e!=null)
            e.buildStackTraceElements(r,Integer.MAX_VALUE);
        return r;
    }

    /**
     * Ignore whatever that we've been doing, and jumps the execution to the given continuation.
     *
     * <p>
     * Aside from the obvious use case of completely overwriting the state of {@link Continuable},
     * more interesting case is
     *
     * Sets aside the current continuation aside, schedule the evaluation of the given block in the given environment,
     * then when done pass the result to the given {@link Continuation}.
     *
     * A common pattern is for that {@link Continuation} to then resume executing the current execution that was set
     * aside.
     *
     * <pre>
     * Continuable c = ...;
     *
     * final Continuable pausePoint = new Continuable(c); // set aside what we were doing
     * c.jump(bodyOfNewThread,env,new Continuation() {
     *      public Next receive(Object o) {
     *          // o is the result of evaluating bodyOfNewThread (the failure will go to the handler specified by 'env')
     *          doSomethingWith(c);
     *
     *          if (...) {// maybe you want to yield this value, then resume from the pause point?
     *              return Next.yield0(new Outcome(o,null),pausePoint);
     *          }
     *          if (...) {// maybe you want to keep going by immediately resuming from the pause point with 'o'
     *              return Next.go0(new Outcome(o,null),pausePoint);
     *          }
     *
     *          // maybe you want to halt the execution by returning
     *          return Next.terminate0(new Outcome(o,null));
     *      }
     * });
     *
     * c.run(...); // this will start executing from 'bodyOfNewThread'
     *
     * </pre>
     */
    public void jump(Continuable c) {
        this.e = c.e;
        this.k = c.k;
    }

    /**
     * Set aside what we are executing, and instead resume the next execution from the point
     * the given 'Continuable' points to.
     *
     * But when that's done, instead of completing the computation, come back to executing what we set aside.
     */
    public void prepend(Continuable c, Function<Outcome,Outcome> mapper) {
        // set aside where we are
        Continuable here = new Continuable(this);

        // run 'c', then when it's done, come back to 'here'
        this.e = c.e;
        this.k = new ConcatenatedContinuation(c.k, mapper, here);
    }

    /*package*/ Env getE() {
        return e;
    }

    /*package*/ Continuation getK() {
        return k;
    }

    private static final long serialVersionUID = 1L;

    /**
     * The artificial {@link StackTraceElement} that appears in the stack trace when the CPS library fixes up
     * the stack trace. This separator separates the regular call stack that tracks the actual call stack
     * JVM executes and the synthesized CPS call stack that CPS-transformed program is logically executing.
     */
    public static final StackTraceElement SEPARATOR_STACK_ELEMENT = new StackTraceElement("___cps", "transform___", null, -2);
}
