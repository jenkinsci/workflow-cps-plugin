package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.sandbox.CallSiteTag;
import com.cloudbees.groovy.cps.sandbox.Invoker;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.MethodClosure;

import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Collections;

/**
 * Method pointer expression: {@code LHS&.methodName}
 *
 * @author Kohsuke Kawaguchi
 */
public class MethodPointerBlock implements CallSiteBlock {
    private final SourceLocation loc;
    private final Block lhsExp;
    private final Block methodNameExp;
    private final Collection<CallSiteTag> tags; // can be null for instances deserialized from the old form

    public MethodPointerBlock(SourceLocation loc, Block lhsExp, Block methodNameExp, Collection<CallSiteTag> tags) {
        this.loc = loc;
        this.lhsExp = lhsExp;
        this.methodNameExp = methodNameExp;
        this.tags = tags;
    }

    @Nonnull
    @Override
    public Collection<CallSiteTag> getTags() {
        return tags !=null ? Collections.unmodifiableCollection(tags) : Collections.<CallSiteTag>emptySet();
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
            return k.receive(e.getInvoker().contextualize(MethodPointerBlock.this).methodPointer(lhs, (String)methodName));
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr fixLhs = new ContinuationPtr(ContinuationImpl.class,"fixLhs");
    static final ContinuationPtr done = new ContinuationPtr(ContinuationImpl.class,"done");
}

