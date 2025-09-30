package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.CaseExpression;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import java.util.List;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;

/**
 * switch/case statement.
 *
 * @author Kohsuke Kawaguchi
 */
public class SwitchBlock implements Block {
    final String label;
    final Block exp;
    final List<CaseExpression> cases;

    /**
     * Statement to run in case there's no match. Can be null.
     */
    final Block default_;

    public SwitchBlock(String label, Block exp, Block default_, List<CaseExpression> cases) {
        this.label = label;
        this.exp = exp;
        this.cases = cases;
        this.default_ = default_;
    }

    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e, k).then(exp, e, test);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        /**
         * {@link Env} to evaluate case statements in, that changes the target of the "break" statement.
         */
        final CaseEnv caseEnv;

        /**
         * Result of evaluating {@link #exp}
         */
        Object switchValue;

        /**
         * {@link CaseExpression} in {@link #cases} that we are testing.
         */
        int index;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
            this.caseEnv = new CaseEnv(e, label, k);
        }

        public Next test(Object value) {
            this.switchValue = value;
            return matcher();
        }

        private Next matcher() {
            if (index < cases.size()) return then(getCase().matcher, e, matcher);
            // run out of all the cases
            if (default_ != null) return then(default_, caseEnv, k);
            else return k.receive(null);
        }

        /**
         * Called after the case expression is evaluated to decide if we are going to run the statement.
         */
        public Next matcher(Object caseValue) {
            boolean b;
            try {
                b = ScriptBytecodeAdapter.isCase(switchValue, caseValue);
            } catch (Throwable t) {
                return throwException(e, t, getCase().loc, new ReferenceStackTrace());
            }

            if (b) {
                // started executing the body
                return body(null);
            } else {
                index++;
                return matcher();
            }
        }

        /**
         * Executes the body and falls through to the next body.
         */
        public Next body(Object unused) {
            if (index == cases.size()) {
                // that was the last block
                return k.receive(null);
            } else {
                Next n = then(getCase().body, caseEnv, body);
                index++;
                return n;
            }
        }

        private CaseExpression getCase() {
            return cases.get(index);
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr test = new ContinuationPtr(ContinuationImpl.class, "test");
    static final ContinuationPtr matcher = new ContinuationPtr(ContinuationImpl.class, "matcher");
    static final ContinuationPtr body = new ContinuationPtr(ContinuationImpl.class, "body");

    private static final long serialVersionUID = 1L;
}
