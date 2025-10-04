package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.CatchExpression;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import java.util.Collections;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class TryCatchBlock implements Block {
    private final List<CatchExpression> catches;
    private final Block body;
    private final Block finally_;

    public TryCatchBlock(List<CatchExpression> catches, Block body, Block finally_) {
        this.catches = catches;
        this.body = body;
        this.finally_ = finally_;
    }

    public Next eval(final Env e, final Continuation k) {
        final TryBlockEnv f = new TryBlockEnv(e, finally_);
        // possible evaluation of the finally block, if present.

        for (final CatchExpression c : catches) {
            f.addHandler(c.type, new Continuation() {
                public Next receive(Object t) {
                    BlockScopeEnv b = new BlockScopeEnv(e, 1);
                    b.declareVariable(c.type, c.name);
                    b.setLocalVariable(c.name, t);

                    // evaluate the body of the catch clause, with the finally block.
                    // that is, at the end of the catch block we want to run the finally block,
                    // and if any jump occurs from within (such as an exception thrown or a break statement),
                    // then we need to run the finally block first.
                    return new Next(
                            new TryCatchBlock(Collections.<CatchExpression>emptyList(), c.handler, finally_), b, k);
                }
            });
        }

        // evaluate the body with the new environment
        return new Next(body, f, f.withFinally(k));
    }

    private static final long serialVersionUID = 1L;
}
