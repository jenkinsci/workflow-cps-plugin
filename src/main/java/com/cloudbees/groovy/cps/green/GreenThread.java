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

    public GreenThread(Next n) {
        this.n = n;
    }

    /**
     * Creates a brand-new thread that evaluates 'b'.
     */
    public GreenThread(Block b) {
        // TODO: allow the caller to pass a value
        this(new Next(b, new FunctionCallEnv(null, null, HALT), HALT));
    }

    /**
     * Creates a {@link GreenThread} that's already dead.
     */
    public GreenThread(Outcome v) {
        this(new Next(null,HALT,v));
    }

    /**
     * Does this thread still have something to execute?
     * If it is dead, n.yield contains the outcome of the thread.
     */
    public boolean isDead() {
        return n.k== Continuation.HALT && n.e==null;
    }

}
