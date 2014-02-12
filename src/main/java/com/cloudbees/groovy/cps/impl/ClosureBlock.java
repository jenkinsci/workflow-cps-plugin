package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

import java.util.List;

/**
 * Closure instantiation: { ... }
 *
 * @author Kohsuke Kawaguchi
 */
public class ClosureBlock implements Block {
    private final List<String> parameters;
    private final Block body;

    public ClosureBlock(List<String> parameters, Block body) {
        this.parameters = parameters;
        this.body = body;
    }

    public Next eval(Env e, Continuation k) {
        return k.receive(new CpsClosure(e.closureOwner(), e.getLocalVariable("this"),
                new CpsClosureDef(parameters,body,e)));
    }

    private static final long serialVersionUID = 1L;
}
