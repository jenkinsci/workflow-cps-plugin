package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.CatchExpression;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

import java.util.List;

/**
* @author Kohsuke Kawaguchi
*/
public class TryCatchBlock implements Block {
    private final List<CatchExpression> catches;
    private final Block body;

    public TryCatchBlock(List<CatchExpression> catches, Block body) {
        this.catches = catches;
        this.body = body;
    }

    public Next eval(final Env e, final Continuation k) {
        final TryBlockEnv f = new TryBlockEnv(e);
        for (final CatchExpression c : catches) {
            f.addHandler(c.type, new Continuation() {
                public Next receive(Object t) {
                    BlockScopeEnv b = new BlockScopeEnv(e);
                    b.declareVariable(c.type, c.name);
                    b.setLocalVariable(c.name, t);

                    return new Next(c.handler, b, k);
                }
            });
        }

        // evaluate the body with the new environment
        return new Next(body,f,k);
    }

    private static final long serialVersionUID = 1L;
}
