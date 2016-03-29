package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.MethodClosure;

/**
 * Method pointer expression: LHS&.methodName
 *
 * @author Kohsuke Kawaguchi
 */
public class MethodPointerBlock implements Block {
    private final SourceLocation loc;
    private final Block lhsExp;
    private final Block methodNameExp;

    public MethodPointerBlock(SourceLocation loc, Block lhsExp, Block methodNameExp) {
        this.loc = loc;
        this.lhsExp = lhsExp;
        this.methodNameExp = methodNameExp;
    }

    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e,k).then(lhsExp,e,fixLhs);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        Object lhs;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        /**
         * Computes {@link #lhs}
         */
        public Next fixLhs(Object lhs) {
            this.lhs = lhs;

            return then(methodNameExp,e,done);
        }

        /**
         * Obtain a method pointer, which is really just a {@link MethodClosure}.
         */
        public Next done(Object methodName) {
            // see AsmClassGenerator.visitMethodPointerExpression
            return k.receive(InvokerHelper.getMethodPointer(lhs,(String)methodName));
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr fixLhs = new ContinuationPtr(ContinuationImpl.class,"fixLhs");
    static final ContinuationPtr done = new ContinuationPtr(ContinuationImpl.class,"done");
}

