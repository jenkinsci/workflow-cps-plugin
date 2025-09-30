package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * do { ... } while ( ... );
 *
 * @author Kohsuke Kawaguchi
 */
public class DoWhileBlock implements Block {
    final Block cond, body;
    final String label;

    public DoWhileBlock(String label, Block body, Block cond) {
        this.label = label;
        this.body = body;
        this.cond = cond;
    }

    public Next eval(Env e, Continuation k) {
        ContinuationImpl c = new ContinuationImpl(e, k);
        return c.top();
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation loopEnd;
        final Env e;

        ContinuationImpl(Env e, Continuation loopEnd) {
            this.e = new LoopBlockScopeEnv(e, label, loopEnd, loopHead.bind(this));
            this.loopEnd = loopEnd;
        }

        public Next top() {
            return then(body, e, loopHead);
        }

        public Next loopHead(Object unused) {
            return then(cond, e, loopCond);
        }

        public Next loopCond(Object cond) {
            return castToBoolean(cond, e, b -> {
                if (b) {
                    // loop
                    return top();
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
