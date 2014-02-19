package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
* @author Kohsuke Kawaguchi
*/
public class ThrowBlock implements Block {
    private final Block exp;

    public ThrowBlock(Block exp) {
        this.exp = exp;
    }

    public Next eval(final Env e, Continuation k) {
        return new Next(exp,e,new Continuation() {
            public Next receive(Object t) {
                if (t==null) {
                    t = new NullPointerException();
                }
                // TODO: fake the stack trace information
                // TODO: what if 't' is not Throwable?

                Continuation v = e.getExceptionHandler(Throwable.class.cast(t).getClass());
                return v.receive(t);
            }
        });
    }

    private static final long serialVersionUID = 1L;
}
