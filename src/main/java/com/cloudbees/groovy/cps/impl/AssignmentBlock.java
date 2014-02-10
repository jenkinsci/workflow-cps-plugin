package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.LValue;
import com.cloudbees.groovy.cps.LValueBlock;
import com.cloudbees.groovy.cps.Next;

/**
 * Assignment operator {@code exp=rhs}
 *
 * TODO: tuple assignment
 * TODO: assignment operators like x+=y
 *
 * @author Kohsuke Kawaguchi
 */
public class AssignmentBlock implements Block {
    private final Block lhsExp,rhsExp;

    public AssignmentBlock(LValueBlock lhsExp, Block rhsExp) {
        this.lhsExp = lhsExp.asLValue();
        this.rhsExp = rhsExp;
    }

    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e,k).then(lhsExp,e,fixLhs);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        LValue lhs;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next fixLhs(Object lhs) {
            this.lhs = (LValue)lhs;
            return then(rhsExp,e,fixRhs);
        }

        public Next fixRhs(Object rhs) {
            return lhs.set(rhs,k);
        }
    }

    static final ContinuationPtr fixLhs = new ContinuationPtr(ContinuationImpl.class,"fixLhs");
    static final ContinuationPtr fixRhs = new ContinuationPtr(ContinuationImpl.class,"fixRhs");
}
