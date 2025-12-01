package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;

/**
 * assert exp : msg;
 *
 * @author Kohsuke Kawaguchi
 */
public class AssertBlock implements Block {
    final Block cond, msg;
    final String sourceText;

    public AssertBlock(Block cond, Block msg, String sourceText) {
        this.cond = cond;
        this.msg = msg;
        this.sourceText = sourceText;
    }

    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e, k).then(cond, e, jump);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next jump(Object cond) {
            return castToBoolean(cond, e, b -> {
                if (b) return k.receive(null);
                else return then(msg, e, fail);
            });
        }

        public Next fail(Object msg) {
            try {
                ScriptBytecodeAdapter.assertFailed(sourceText, msg);
                throw new IllegalStateException(); // assertFailed will throw an exception
            } catch (Throwable t) {
                return e.getExceptionHandler(t.getClass()).receive(t);
            }
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr jump = new ContinuationPtr(ContinuationImpl.class, "jump");
    static final ContinuationPtr fail = new ContinuationPtr(ContinuationImpl.class, "fail");

    private static final long serialVersionUID = 1L;
}
