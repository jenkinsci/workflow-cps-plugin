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
 *
 * @author Kohsuke Kawaguchi
 */
public class AssignmentBlock implements Block {
    private final Block lhsExp,rhsExp;

    /**
     * For compound assignment operator (such as ^=), set the operator method here.
     */
    private final String compoundOp;

    public AssignmentBlock(LValueBlock lhsExp, Block rhsExp, String compoundOp) {
        this.compoundOp = compoundOp;
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
        Object rhs,cur;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        /**
         * Computes {@link LValue}
         */
        public Next fixLhs(Object lhs) {
            this.lhs = (LValue)lhs;

            if (compoundOp==null)
                return then(rhsExp,e,assignAndDone);
            else
                return ((LValue) lhs).get(fixCur.bind(this));
        }

        /**
         * Just straight assignment from RHS to LHS, then done
         */
        public Next assignAndDone(Object rhs) {
            return lhs.set(rhs,k);  // just straight assignment
        }

        /**
         * Computed the current value of {@link LValue} for compound assignment.
         * Evaluate rhs.
         */
        public Next fixCur(Object cur) {
            this.cur = cur;
            return then(rhsExp,e,fixRhs);
        }

        /**
         * Evaluated rhs.
         * Invoke the operator
         */
        public Next fixRhs(Object rhs) {
            return methodCall(e, assignAndDone, this.cur, compoundOp, rhs);
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr fixLhs = new ContinuationPtr(ContinuationImpl.class,"fixLhs");
    static final ContinuationPtr assignAndDone = new ContinuationPtr(ContinuationImpl.class,"assignAndDone");
    static final ContinuationPtr fixCur = new ContinuationPtr(ContinuationImpl.class,"fixCur");
    static final ContinuationPtr fixRhs = new ContinuationPtr(ContinuationImpl.class,"fixRhs");

    private static final long serialVersionUID = 1L;
}
