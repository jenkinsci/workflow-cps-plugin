package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.sandbox.CallSiteTag;
import java.util.Collection;
import java.util.List;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;

public class CastBlock extends CallSiteBlockSupport {
    private final SourceLocation loc;
    private final Block valueExp;
    private final Class<?> type;
    private final boolean ignoreAutoboxing;
    private final boolean coerce;
    private final boolean strict;

    public CastBlock(
            SourceLocation loc,
            Collection<CallSiteTag> tags,
            Block valueExp,
            Class<?> type,
            boolean ignoreAutoboxing,
            boolean coerce,
            boolean strict) {
        super(tags);
        this.loc = loc;
        this.valueExp = valueExp;
        this.type = type;
        this.ignoreAutoboxing = ignoreAutoboxing;
        this.coerce = coerce;
        this.strict = strict;
    }

    @Override
    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e, k).then(valueExp, e, cast);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next cast(Object value) {
            try {
                return k.receive(e.getInvoker()
                        .contextualize(CastBlock.this)
                        .cast(value, type, ignoreAutoboxing, coerce, strict));
            } catch (CpsCallableInvocation inv) {
                // Implementations of asType and other methods used by the Groovy stdlib should be @NonCPS, but we
                // just log a warning and invoke the callable anyway to maintain the existing behavior.
                inv.checkMismatch(ScriptBytecodeAdapter.class, List.of(coerce ? "asType" : "castToType"));
                return inv.invoke(e, loc, k);
            } catch (Throwable t) {
                if (t instanceof InvokerInvocationException) {
                    // DefaultTypeTransformation calls asBoolean via InvokerHelper, which wraps all thrown exceptions
                    // in InvokerInvocationException. CpsCallableInvocation in this context has always resulted in
                    // "Unexpected exception in CPS VM thread", so there is no need to attempt to recover by invoking
                    // the callable.
                    Throwable cause = t.getCause();
                    if (cause instanceof CpsCallableInvocation) {
                        CpsCallableInvocation inv = (CpsCallableInvocation) cause;
                        inv.checkMismatch(ScriptBytecodeAdapter.class, List.of(coerce ? "asType" : "castToType"));
                        String classAndMethod = inv.getClassAndMethodForDisplay();
                        t = new IllegalStateException(classAndMethod
                                + " must be @NonCPS; see: https://jenkins.io/redirect/pipeline-cps-method-mismatches/");
                    }
                }
                return throwException(e, t, loc, new ReferenceStackTrace());
            }
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr cast = new ContinuationPtr(ContinuationImpl.class, "cast");

    private static final long serialVersionUID = 1L;
}
