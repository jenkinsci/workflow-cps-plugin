package com.cloudbees.groovy.cps;

/**
 * Remaining computation to execute. To work around the lack of tail-call optimization
 *
 * @author Kohsuke Kawaguchi
 */
public class Next {
    Block f;
    Env e;
    Continuation k;

    /**
     * If the program getting executed wants to yield a value and suspend its execution,
     * this value is set to non-null.
     *
     * {@link #NULL} is used to yield null.
     */
    private Object yield;

    public Next(Block f, Env e, Continuation k) {
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

    /*package*/ void yield(Object v) {
        if (v==null)  v = NULL;
        this.yield = v;
    }

    /*package*/ Object yieldedValue() {
        if (yield==NULL)    return null;
        return yield;
    }

    private static final class Null {}
    private static final Null NULL = new Null();
}
