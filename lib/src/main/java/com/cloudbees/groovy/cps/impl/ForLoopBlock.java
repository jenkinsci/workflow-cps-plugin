package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * for (e1; e2; e3) { ... body ... }
 *
 * @author Kohsuke Kawaguchi
 */
public class ForLoopBlock implements Block {
    final Block e1, e2, e3, body;
    final String label;

    public ForLoopBlock(String label, Block e1, Block e2, Block e3, Block body) {
        this.label = label;
        this.e1 = e1;
        this.e2 = e2;
        this.e3 = e3;
        this.body = body;
    }

    public Next eval(Env e, Continuation k) {
        ContinuationImpl c = new ContinuationImpl(e, k);
        return c.then(e1, c.e, loopHead);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation loopEnd;
        final Env e;

        ContinuationImpl(Env e, Continuation loopEnd) {
            this.e = new LoopBlockScopeEnv(e, label, loopEnd, increment.bind(this));
            this.loopEnd = loopEnd;
        }

        public Next loopHead(Object unused) {
            return then(e2, e, loopCond);
        }

        public Next loopCond(Object cond) {
            return castToBoolean(cond, e, b -> {
                if (b) {
                    // loop
                    return then(body, e, increment);
                } else {
                    // exit loop
                    return loopEnd.receive(null);
                }
            });
        }

        public Next increment(Object unused) {
            return then(e3, e, loopHead);
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr loopHead = new ContinuationPtr(ContinuationImpl.class, "loopHead");
    static final ContinuationPtr loopCond = new ContinuationPtr(ContinuationImpl.class, "loopCond");
    static final ContinuationPtr increment = new ContinuationPtr(ContinuationImpl.class, "increment");

    private static final long serialVersionUID = 1L;
}
