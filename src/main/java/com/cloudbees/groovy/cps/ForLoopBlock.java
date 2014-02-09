package com.cloudbees.groovy.cps;

/**
 * for (e1; e2; e3) { ... body ... }
 *
 * @author Kohsuke Kawaguchi
 */
public class ForLoopBlock implements Expression {
    final Expression e1, e2, e3, body;

    public ForLoopBlock(Expression e1, Expression e2, Expression e3, Expression body) {
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
            this.e = new BlockScopeEnv(e);
            this.loopEnd = loopEnd;
        }

        public Next loopHead(Object _) {
            return then(e2, e, loopCond);
        }

        public Next loopCond(Object cond) {
            if (asBoolean(cond)) {
                // loop
                return then(body,e,increment);
            } else {
                // exit loop
                return loopEnd.receive(null);
            }
        }

        public Next increment(Object _) {
            return then(e3,e,loopHead);
        }
    }

    static final ContinuationPtr loopHead = new ContinuationPtr(ContinuationImpl.class,"loopHead");
    static final ContinuationPtr loopCond = new ContinuationPtr(ContinuationImpl.class,"loopCond");
    static final ContinuationPtr increment = new ContinuationPtr(ContinuationImpl.class,"increment");

}
