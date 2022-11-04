package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * "continue" statement to repeat a loop.
 *
 * @author Kohsuke Kawaguchi
 */
public class ContinueBlock implements Block {
    private final String label;

    public ContinueBlock(String label) {
        this.label = label;
    }

    public Next eval(Env e, Continuation k) {
        return e.getContinueAddress(label).receive(null);
    }

    public static final ContinueBlock INSTANCE = new ContinueBlock(null);

    private static final long serialVersionUID = 1L;
}
