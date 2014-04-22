package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.impl.FunctionCallEnv;
import com.cloudbees.groovy.cps.Outcome;

import java.io.Serializable;

import static com.cloudbees.groovy.cps.Continuation.*;

/**
 * Current state of a green thread.
 *
 * <p>
 * When we fork {@link Continuable}, we want to fork all threads, so this object is immutable,
 * and every time a thread moves forward, a new object gets created.
 *
 * @author Kohsuke Kawaguchi
 */
final class GreenThreadState implements Serializable {
    /**
     * Remaining computation to execute on this thread.
     * The equivalent of a program counter.
     */
    final Next n;

    /**
     * Unique ID among other {@link GreenThreadState}s in {@link GreenWorld}
     */
    final GreenThread g;

    final Monitor monitor;

    /**
     * Meaning of the {@link #wait} field.
     */
    final Cond cond;

    /**
     * Monitor that's causing us to block.
     */
    final Object wait;

    private GreenThreadState(GreenThread g, Next n, Monitor monitor, Cond cond, Object wait) {
        this.g = g;
        this.n = n;
        this.monitor = monitor;
        this.cond = cond;
        this.wait = wait;

        // either both must be null or both must be non-null
        assert (cond==null) == (wait==null);
    }

    private GreenThreadState(GreenThread g, Next n) {
        this(g,n,null,null,null);
    }

    /**
     * Creates a brand-new thread that evaluates 'b'.
     */
    GreenThreadState(GreenThread g, Block b) {
        // TODO: allow the caller to pass a value
        this(g,new Next(b, new FunctionCallEnv(null, null, HALT), HALT));
    }

    /**
     * Creates a {@link GreenThreadState} that's already dead.
     */
    GreenThreadState(GreenThread g, Outcome v) {
        this(g,new Next(null,HALT,v));
    }

// methods for changing one state at a time
    GreenThreadState with(Next n) {
        return new GreenThreadState(g,n,monitor,cond,wait);
    }

    GreenThreadState with(Monitor monitor) {
        return new GreenThreadState(g,n,monitor,cond,wait);
    }

    GreenThreadState withCond(Cond cond, Object o) {
        return new GreenThreadState(g,n,monitor,cond,o);
    }

    GreenThreadState pushMonitor(Object o) {
        return with(new Monitor(monitor,o));
    }

    GreenThreadState popMonitor() {
        return with(monitor.next);
    }


    /**
     * Can this thread be scheduled for execution, or does it need to sleep (relative to other green threads)?
     *
     * Note that if a whole {@link Continuable} is suspended, the thread is considered still runnable.
     */
    boolean isRunnable() {
        return cond==null;
    }

    /**
     * Does this thread still have something to execute?
     * If it is dead, n.yield contains the outcome of the thread.
     */
    public boolean isDead() {
        return n.k== Continuation.HALT && n.e==null;
    }

    public Outcome getResult() {
        if (isDead())   return n.yield;
        else            throw new IllegalStateException("Green thread is still running");
    }

    /**
     * Runs one step in this thread and returns a new state.
     */
    /*package*/ GreenThreadState tick(Object o) {
        return resumeFrom(new Outcome(o,null));
    }

    /*package*/ GreenThreadState resumeFrom(Outcome o) {
        return with(o.resumeFrom(n.e, n.k));
    }

    /*package*/ GreenThreadState step() {
        return with(n.step());
    }

    /**
     * Does this thread already own the monitor of 'o'?
     */
    boolean hasMonitor(Object o) {
        if (wait==o && (cond==Cond.WAIT || cond==Cond.NOTIFIED)) {
            // this thread owns the monitor but it is released temporarily
            return false;
        }

        for (Monitor m = monitor; m!=null; m=m.next)
            if (m.o==o)
                return true;
        return false;
    }

    private static final long serialVersionUID = 1L;
}
