package com.cloudbees.groovy.cps;

/**
 * Remaining computation to execute. To work around the lack of tail-call optimization
 *
 * @author Kohsuke Kawaguchi
 */
public class Next {
    Expression f;
    Env e;
    Continuation k;

    /**
     * If the program getting executed wants to yield a value and suspend its execution,
     * this value is set to non-null.
     */
    Object yield;

    public Next(Expression f, Env e, Continuation k) {
        this.f = f;
        this.e = e;
        this.k = k;
    }

    /**
     * Resumes the execution of this program state, until it yields a value or finishes computation.
     */
    public Next resume() {
        Next n = this;
        do {
            n = n.f.eval(n.e, n.k);
        } while(n.yield==null);
        return n;
    }
}
