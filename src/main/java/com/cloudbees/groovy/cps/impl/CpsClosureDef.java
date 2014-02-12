package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
class CpsClosureDef extends CpsCallable {
    /**
     * Environment that was captured as of closure instantiation.
     */
    private final Env capture;

    public CpsClosureDef(List<String> parameters, Block body, Env capture) {
        super(parameters, body);
        this.capture = capture;
    }

    @Override
    Next invoke(Env caller, Object receiver, List<?> args, Continuation k) {
        Env e = new ClosureCallEnv(caller, k, capture, (CpsClosure)receiver);

        assignArguments(args, e);

        // TODO: who handles 'it' ?

        return new Next(body, e, k);
    }
}
