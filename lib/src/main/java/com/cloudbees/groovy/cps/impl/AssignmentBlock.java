package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.LValue;
import com.cloudbees.groovy.cps.LValueBlock;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.sandbox.CallSiteTag;
import java.util.Collection;

/**
 * Assignment operator {@code exp=rhs}
 *
 * TODO: tuple assignment
 *
 * @author Kohsuke Kawaguchi
 */
public class AssignmentBlock extends CallSiteBlockSupport {
    private final Block lhsExp, rhsExp;

    /**
     * For compound assignment operator (such as ^=), set the operator method here.
     */
    private final String compoundOp;

    private final SourceLocation loc;

    public AssignmentBlock(
            SourceLocation loc, Collection<CallSiteTag> tags, LValueBlock lhsExp, Block rhsExp, String compoundOp) {
        super(tags);
        this.loc = loc;
        this.compoundOp = compoundOp;
        this.lhsExp = lhsExp.asLValue();
        this.rhsExp = rhsExp;
    }

    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e, k).then(lhsExp, e, fixLhs);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        LValue lhs;
        Object rhs, cur;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        /**
         * Computes {@link LValue}
         */
        public Next fixLhs(Object lhs) {
            this.lhs = (LValue) lhs;

            if (compoundOp == null) return then(rhsExp, e, assignAndDone);
            else return ((LValue) lhs).get(fixCur.bind(this));
        }

        /**
         * Assign from RHS to LHS and pass RHS to the continuation.
         */
        public Next assignAndDone(Object rhs) {
            return lhs.set(rhs, then(new ConstantBlock(rhs), e, k));
        }

        /**
         * Computed the current value of {@link LValue} for compound assignment.
         * Evaluate rhs.
         */
        public Next fixCur(Object cur) {
            this.cur = cur;
            return then(rhsExp, e, fixRhs);
        }

        /**
         * Evaluated rhs.
         * Invoke the operator
         */
        public Next fixRhs(Object rhs) {
            return methodCall(e, loc, assignAndDone, AssignmentBlock.this, this.cur, compoundOp, rhs);
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr fixLhs = new ContinuationPtr(ContinuationImpl.class, "fixLhs");
    static final ContinuationPtr assignAndDone = new ContinuationPtr(ContinuationImpl.class, "assignAndDone");
    static final ContinuationPtr fixCur = new ContinuationPtr(ContinuationImpl.class, "fixCur");
    static final ContinuationPtr fixRhs = new ContinuationPtr(ContinuationImpl.class, "fixRhs");

    private static final long serialVersionUID = 1L;
}
