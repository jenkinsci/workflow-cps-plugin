package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.LValue;
import com.cloudbees.groovy.cps.LValueBlock;
import com.cloudbees.groovy.cps.Next;

/**
 * Access to local variables and method parameters.
 *
 * @author Kohsuke Kawaguchi
 */
public class LocalVariableBlock extends LValueBlock {
    private final String name;

    public LocalVariableBlock(String name) {
        this.name = name;
    }

    public Next evalLValue(final Env e, Continuation k) {
        return k.receive(new LValue() {
            public Next get(Continuation k) {
                return k.receive(e.getLocalVariable(name));
            }

            public Next set(Object v, Continuation k) {
                e.setLocalVariable(name,v);
                return k.receive(null);
            }
        });
    }

    private static final long serialVersionUID = 1L;
}
