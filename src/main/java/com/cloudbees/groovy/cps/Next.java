package com.cloudbees.groovy.cps;

import static com.cloudbees.groovy.cps.Continuation.HALT;

/**
 * Remaining computation to execute. To work around the lack of tail-call optimization
 *
 * @author Kohsuke Kawaguchi
 */
public class Next {
    Expression f;
    Env e;
    Continuation k;
    Object[] args;

    /**
     * If the program getting executed wants to yield a value and suspend its execution,
     * this value is set to non-null.
     */
    Object yield;

    public Next(Expression f, Env e, Continuation k, Object... args) {
        this.f = f;
        this.e = e;
        this.k = k;
        this.args = args;
    }

    public static Next start(Expression f, Object... args) {
        return new Next(f,new Env(), HALT, args);
    }

    /**
     * Resumes the execution of this program state, until it yields a value or finishes computation.
     */
    public Next resume() {
        Next n = this;
        do {
            n = n.f.eval(n.e, n.k, n.args);
        } while(n.yield==null);
        return n;
    }
}
