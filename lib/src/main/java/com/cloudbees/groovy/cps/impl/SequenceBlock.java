package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * exp1; exp2
 *
 * @author Kohsuke Kawaguchi
 */
public class SequenceBlock implements Block {
    private final Block exp1;
    private final Block exp2;

    public SequenceBlock(Block exp1, Block exp2) {
        this.exp1 = exp1;
        this.exp2 = exp2;
    }

    public Next eval(final Env e, final Continuation k) {
        return new Next(exp1, e, new ContinuationImpl(e, k, exp2));
    }

    private static class ContinuationImpl implements Continuation {
        private final Env e;
        private final Continuation k;
        private final Block exp2;

        public ContinuationImpl(Env e, Continuation k, Block exp2) {
            this.e = e;
            this.k = k;
            this.exp2 = exp2;
        }

        public Next receive(Object unused) {
            return new Next(exp2, e, k);
        }

        private static final long serialVersionUID = 1L;
    }
}
