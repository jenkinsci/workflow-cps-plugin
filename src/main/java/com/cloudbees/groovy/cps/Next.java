package com.cloudbees.groovy.cps;

import java.io.Serializable;

/**
 * Remaining computation to execute. To work around the lack of tail-call optimization
 *
 * @author Kohsuke Kawaguchi
 */
public final class Next implements Serializable, Continuation {
    final Block f;
    final Env e;
    final Continuation k;

    /**
     * If the program getting executed wants to yield a value and suspend its execution,
     * this value is set to non-null.
     *
     * This field and {@link #f} is mutually exclusive.
     */
    /*package*/ Outcome yield;

    public Next(Block f, Env e, Continuation k) {
        this.f = f;
        this.e = e;
        this.k = k;
    }

    /**
     * Resumes the execution of this program state, until it yields a value or finishes computation.
     */
    public Next run() {
        Next n = this;
        while(n.yield==null) {
            n = n.step();
        }
        return n;
    }

    /**
     * Executes one step
     */
    public Next step() {
        return f.eval(e, k);
    }

    /**
     * Creates a {@link Next} object that
     * causes the interpreter loop to exit with the specified value, then optionally allow the interpreter
     * to resume with the specified {@link Continuation}.
     */
    public static Next yield(Object v, Env e, Continuation k) {
        return yield0(new Outcome(v, null), e, k);
    }

    private static Next yield0(Outcome v, Env e, Continuation k) {
        if (v==null)        throw new IllegalStateException("trying to yield null");

        Next n = new Next(null,e,k);
        n.yield = v;

        return n;
    }

    /**
     * Creates a {@link Next} object that terminates the computation and either returns a value or throw an exception.
     */
    public static Next terminate(Object v) {
        return terminate(new Outcome(v, null));
    }

    static Next terminate(Outcome v) {
        return yield0(v,null,HALT);
    }
      
    public static Next unhandledException(Throwable t) {
        return terminate(new Outcome(null, t));
    }
      
    /**
     * As a {@link Continuation}, just ignore the argument.
     */
    public Next receive(Object _) {
        return this;
    }

    private static final long serialVersionUID = 1L;
}
