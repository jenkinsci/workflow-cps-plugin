package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.impl.FunctionCallEnv;
import com.cloudbees.groovy.cps.impl.Outcome;

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
class GreenThreadState {
    /**
     * Remaining computation to execute on this thread.
     * The equivalent of a program counter.
     */
    final Next n;

    /**
     * Unique ID among other {@link GreenThreadState}s in {@link GreenDispatcher}
     */
    final GreenThread g;

    GreenThreadState(GreenThread g, Next n) {
        this.g = g;
        this.n = n;
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
        return new GreenThreadState(this.g, o.resumeFrom(n.e, n.k));
    }

    /*package*/ GreenThreadState step() {
        return new GreenThreadState(this.g, n.step());
    }
}
