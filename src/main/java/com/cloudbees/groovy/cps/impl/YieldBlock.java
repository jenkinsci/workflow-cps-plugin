package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * Yield a value and suspend the execution of the program.
 *
 * @author Kohsuke Kawaguchi
 * @see Continuable#suspend(Object)
 */
public class YieldBlock implements Block {
    private final Object v;

    public YieldBlock(Object v) {
        this.v = v;
    }

    public Next eval(Env e, Continuation k) {
        return Next.yield(new Conclusion(v,null), new YieldContinuation(k));
    }

    private static final long serialVersionUID = 1L;

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
    private static class YieldContinuation implements Continuation {
        private final Continuation k;

        public YieldContinuation(Continuation k) {
            this.k = k;
        }

        public Next receive(Object o) {
            return new Next(new ConstantBlock(o),null, k);
        }

        private static final long serialVersionUID = 1L;
    }
}
