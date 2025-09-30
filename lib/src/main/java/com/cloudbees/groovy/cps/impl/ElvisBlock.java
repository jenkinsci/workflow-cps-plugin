package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * x ?: y
 *
 * @author Kohsuke Kawaguchi
 */
public class ElvisBlock implements Block {
    final Block cond, falseExp;

    public ElvisBlock(Block cond, Block falseExp) {
        this.cond = cond;
        this.falseExp = falseExp;
    }

    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e, k).then(cond, e, jump);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next jump(Object cond) {
            return castToBoolean(cond, e, b -> {
                if (b) {
                    return k.receive(cond);
                } else {
                    return then(falseExp, e, k);
                }
            });
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr jump = new ContinuationPtr(ContinuationImpl.class, "jump");

    private static final long serialVersionUID = 1L;
}
