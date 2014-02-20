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
class SequenceBlock implements Block {
    private final Block exp1;
    private final Block exp2;

    public SequenceBlock(Block exp1, Block exp2) {
        this.exp1 = exp1;
        this.exp2 = exp2;
    }

    public Next eval(final Env e, final Continuation k) {
        return new Next(exp1,e,new Continuation() {
            public Next receive(Object __) {
                return new Next(exp2,e,k);
            }
        });
    }
}
