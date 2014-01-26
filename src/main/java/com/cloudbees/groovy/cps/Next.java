package com.cloudbees.groovy.cps;

/**
 * Remaining computation to execute. To work around the lack of tail-call optimization
 *
 * @author Kohsuke Kawaguchi
 */
public class Next {
    Function f;
    Env e;
    Continuation k;
    Object[] args;

    /**
     * If the program getting executed wants to yield a value and suspend its execution,
     * this value is set to non-null.
     */
    Object yield;

    public Next(Function f, Env e, Continuation k, Object... args) {
        this.f = f;
        this.e = e;
        this.k = k;
        this.args = args;
    }

    public static Next start(Function f, Object... args) {
        return new Next(f,new Env(),)
    }
}
