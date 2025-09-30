package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * if (...) { ... } else { ... }
 *
 * @author Kohsuke Kawaguchi
 */
public class IfBlock implements Block {
    final Block cond, then, els;

    public IfBlock(Block cond, Block then, Block els) {
        this.cond = cond;
        this.then = then;
        this.els = els;
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
            return castToBoolean(cond, e, b -> then(b ? then : els, e, k));
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr jump = new ContinuationPtr(ContinuationImpl.class, "jump");

    private static final long serialVersionUID = 1L;
}
