package com.cloudbees.groovy.cps;

import groovy.lang.Closure;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class CpsClosure extends Closure {
    private final CpsClosureDef def;

    public CpsClosure(Object owner, Object thisObject, CpsClosureDef def) {
        super(owner, thisObject);
        this.def = def;
    }

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
