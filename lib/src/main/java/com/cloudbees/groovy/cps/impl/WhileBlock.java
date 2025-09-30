package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * while(...) { ... }
 *
 * @author Kohsuke Kawaguchi
 */
public class WhileBlock implements Block {
    final Block cond, body;
    final String label;

    public WhileBlock(String label, Block cond, Block body) {
        this.label = label;
        this.cond = cond;
        this.body = body;
    }

    public Next eval(Env e, Continuation k) {
        ContinuationImpl c = new ContinuationImpl(e, k);
        return c.loopHead(null);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation loopEnd;
        final Env e;

        ContinuationImpl(Env e, Continuation loopEnd) {
            this.e = new LoopBlockScopeEnv(e, label, loopEnd, loopHead.bind(this));
            this.loopEnd = loopEnd;
        }

        public Next loopHead(Object unused) {
            return then(cond, e, loopCond);
        }

        public Next loopCond(Object cond) {
            return castToBoolean(cond, e, b -> {
                if (b) {
                    // loop
                    return then(body, e, loopHead);
                } else {
                    // exit loop
                    return loopEnd.receive(null);
                }
            });
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr loopHead = new ContinuationPtr(ContinuationImpl.class, "loopHead");
    static final ContinuationPtr loopCond = new ContinuationPtr(ContinuationImpl.class, "loopCond");

    private static final long serialVersionUID = 1L;
}
