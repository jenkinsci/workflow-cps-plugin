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
 * "++x", "--x", "x++", or "x--" operator.
 *
 * This class is so named by Jesse Glick. When asked if my dictionary look up on the word "excrement" is accurate,
 * he said: "given the number of stupid bugs caused by misuse of this syntax, yes!"
 * So there we go.
 *
 * @author Kohsuke Kawaguchi
 */
public class ExcrementOperatorBlock extends CallSiteBlockSupport {
    /**
     * "previous" for decrement and "next" for increment.
     */
    private final String operatorMethod;

    /**
     * True if this is a prefix operator, false if it's a postfix.
     */
    private final boolean prefix;

    private final Block body;

    private final SourceLocation loc;

    public ExcrementOperatorBlock(
            SourceLocation loc, Collection<CallSiteTag> tags, String operatorMethod, boolean prefix, LValueBlock body) {
        super(tags);
        this.loc = loc;
        this.operatorMethod = operatorMethod;
        this.prefix = prefix;
        this.body = body.asLValue();
    }

    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e, k).then(body, e, fixLhs);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        LValue lhs;
        Object before, after;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        /**
         * LValue evaluated, then get the current value
         */
        public Next fixLhs(Object lhs) {
            this.lhs = (LValue) lhs;
            return this.lhs.get(fixCur.bind(this));
        }

        /**
         * Computed the current value of {@link LValue}.
         * Next, evaluate the operator and capture the result
         */
        public Next fixCur(Object v) {
            this.before = v;
            return methodCall(e, loc, calc, ExcrementOperatorBlock.this, v, operatorMethod);
        }

        /**
         * Result of the operator application obtained.
         * Next, update the value.
         */
        public Next calc(Object v) {
            this.after = v;

            // update the value.
            return this.lhs.set(after, done.bind(this));
        }

        /**
         * The result of the evaluation of the entire result depends on whether this is prefix or postfix
         */
        public Next done(Object unused) {
            return k.receive(prefix ? after : before);
        }
    }

    static final ContinuationPtr fixLhs = new ContinuationPtr(ContinuationImpl.class, "fixLhs");
    static final ContinuationPtr fixCur = new ContinuationPtr(ContinuationImpl.class, "fixCur");
    static final ContinuationPtr calc = new ContinuationPtr(ContinuationImpl.class, "calc");
    static final ContinuationPtr done = new ContinuationPtr(ContinuationImpl.class, "done");

    private static final long serialVersionUID = 1L;
}
