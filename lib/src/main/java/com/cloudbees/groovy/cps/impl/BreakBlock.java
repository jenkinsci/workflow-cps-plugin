package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * "break" statement to exit a loop.
 *
 * @author Kohsuke Kawaguchi
 */
public class BreakBlock implements Block {
    private final String label;

    public BreakBlock(String label) {
        this.label = label;
    }

    public Next eval(Env e, Continuation k) {
        return e.getBreakAddress(label).receive(null);
    }

    public static final BreakBlock INSTANCE = new BreakBlock(null);

    private static final long serialVersionUID = 1L;
}
