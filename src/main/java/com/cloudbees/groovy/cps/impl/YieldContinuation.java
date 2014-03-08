package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.Resumable;

/**
 * {@link Continuable#isResumable()} uses {@link #HALT} to determine
 * the end of the program, and it gets confused if the end of the program
 * follows immediately after the yield statement (or more likely {@link Continuable#suspend(Object)}
 * since yield is not a language-reserved statement.)
 *
 * <p>
 * So we don't want to just use 'k' in {@link YieldBlock#eval(Env, Continuation)} as the continuation.
 * We want to insert one extra step to make sure that the {@link Continuable} executes the 2nd half
 * of the yield block.
 */
public class YieldContinuation implements Resumable {
    private final Env e;
    private final Continuation k;

    public YieldContinuation(Env e, Continuation k) {
        this.e = e;
        this.k = k;
    }

    /**
     * When {@link Continuable#run(Object)} is called, the argument comes here.
     */
    public Next receive(Conclusion o) {
        Conclusion cn = (Conclusion) o;
        Throwable t = cn.getAbnormal();
        if (t!=null)
            return new Next(new ThrowBlock(new ConstantBlock(t)),e,k);
        else
            return new Next(new ConstantBlock(cn.getNormal()),e, k);
    }

    private static final long serialVersionUID = 1L;
}
