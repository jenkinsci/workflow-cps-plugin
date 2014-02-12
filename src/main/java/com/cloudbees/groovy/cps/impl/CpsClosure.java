package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Env;
import groovy.lang.Closure;

import java.util.List;

/**
 * {@link Closure} whose code is CPS-transformed.
 *
 * @author Kohsuke Kawaguchi
 */
public class CpsClosure extends Closure {
    private final CpsClosureDef def;

    public CpsClosure(Object owner, Object thisObject, List<String> parameters, Block body, Env capture) {
        super(owner, thisObject);
        this.def = new CpsClosureDef(parameters,body,capture,this);
        // TODO: parameterTypes and maximumNumberOfParameters
    }

    // returning CpsCallable lets the caller know that it needs to do CPS evaluation of this closure.
    @Override
    public Object call() {
        return def;
    }

    @Override
    public Object call(Object... args) {
        return def;
    }

    @Override
    public Object call(Object arguments) {
        return def;
    }
}
