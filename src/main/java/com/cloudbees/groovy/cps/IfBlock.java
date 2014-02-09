package com.cloudbees.groovy.cps;

/**
 * if (...) { ... } else { ... }
 *
 * @author Kohsuke Kawaguchi
 */
public class IfBlock implements Expression {
    final Expression cond, then, els;

    public IfBlock(Expression cond, Expression then, Expression els) {
        this.cond = cond;
        this.then = then;
        this.els = els;
    }

    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e,k).then(cond,e,jump);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next jump(Object cond) {
            return then(asBoolean(cond) ? then : els,e,k);
        }
    }

    static final ContinuationPtr jump = new ContinuationPtr(ContinuationImpl.class,"jump");

}
