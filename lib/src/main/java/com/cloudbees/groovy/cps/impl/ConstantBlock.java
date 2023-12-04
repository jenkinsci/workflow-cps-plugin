package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * Constant value.
 *
 * @author Kohsuke Kawaguchi
 */
public class ConstantBlock implements Block {
    public final Object value;

    public ConstantBlock(Object value) {
        this.value = value;
    }

    public Next eval(Env e, Continuation k) {
        return k.receive(value);
    }

    private static final long serialVersionUID = 1L;
}
