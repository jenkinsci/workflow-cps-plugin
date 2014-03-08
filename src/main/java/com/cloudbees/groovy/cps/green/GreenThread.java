package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.impl.Outcome;
import com.cloudbees.groovy.cps.impl.FunctionCallEnv;

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
public class GreenThread {
    /**
     * Remaining computation to execute on this thread.
     * The equivalent of a program counter.
     */
    final Next n;

    /**
     * Unique ID among other {@link GreenThread}s in {@link GreenDispatcher}
     */
    final int id;

    GreenThread(int id, Next n) {
        this.id = id;
        this.n = n;
    }

    /**
     * Creates a brand-new thread that evaluates 'b'.
     */
    GreenThread(int id, Block b) {
        // TODO: allow the caller to pass a value
        this(id,new Next(b, new FunctionCallEnv(null, null, HALT), HALT));
    }

    /**
     * Creates a {@link GreenThread} that's already dead.
     */
    GreenThread(int id, Outcome v) {
        this(id,new Next(null,HALT,v));
    }

    /**
     * Does this thread still have something to execute?
     * If it is dead, n.yield contains the outcome of the thread.
     */
    public boolean isDead() {
        return n.k== Continuation.HALT && n.e==null;
    }

    /**
     * Runs one step in this thread and returns a new state.
     */
    /*package*/ GreenThread tick(Object o) {
        Next n = this.n.k.receive(o);
        return new GreenThread(id, n);
    }

    /*package*/ GreenThread step() {
        Next n = this.n.step();
        return new GreenThread(id, n);
    }
}
