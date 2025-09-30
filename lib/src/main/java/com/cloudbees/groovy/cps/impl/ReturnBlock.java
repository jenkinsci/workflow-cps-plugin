package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * @author Kohsuke Kawaguchi
 */
public class ReturnBlock implements Block {
    private final Block exp;

    public ReturnBlock(Block exp) {
        this.exp = exp;
    }

    public Next eval(Env e, Continuation k) {
        return new Next(exp, e, e.getReturnAddress());
    }

    private static final long serialVersionUID = 1L;
}
