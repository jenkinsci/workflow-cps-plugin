package com.cloudbees.groovy.cps;

import java.util.HashMap;
import java.util.Map;

/**
 * For variable lookup. This is local variables.
 *
 * @author Kohsuke Kawaguchi
 */
public class Env {
    // TODO: How do we correctly assign local variables to its scope?

    // TODO: delegate?
    final Map<String,Object> locals = new HashMap<String, Object>();

    /**
     * If this environment is created for a function call, this field retains the return address.
     */
    protected Continuation returnAddress;

    Env(Object _this) {
        locals.put("this",_this);
    }

    public Object get(String name) {
        return locals.get(name);
    }

    public void set(String name, Object value) {
        locals.put(name,value);
    }

    /**
     * Creates a new block scope, which doesn't hide current variables, but newly declared variables
     * will be local to the new environment.
     */
    public Env newBlockScope() {
        // TODO
        return this;
    }
}
