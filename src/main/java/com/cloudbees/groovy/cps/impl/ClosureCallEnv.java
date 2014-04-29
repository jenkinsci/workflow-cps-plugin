package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link Env} for evaluating the body of a closure.
 *
 * @author Kohsuke Kawaguchi
 */
class ClosureCallEnv extends CallEnv {
    final Map<String,Object> locals = new HashMap<String, Object>();

    final CpsClosure closure;

    /**
     * Environment captured at the closure instantiation.
     */
    final Env captured;

    public ClosureCallEnv(Env caller, Continuation returnAddress, SourceLocation loc, Env captured, CpsClosure closure) {
        super(caller,returnAddress,loc);
        this.closure = closure;
        this.captured = captured;
    }

    public void declareVariable(Class type, String name) {
        locals.put(name,null);
    }

    public Object getLocalVariable(String name) {
        if (locals.containsKey(name))
            return locals.get(name);
        else
            return captured.getLocalVariable(name);
    }

    public void setLocalVariable(String name, Object value) {
        if (locals.containsKey(name))
            locals.put(name, value);
        else
            captured.setLocalVariable(name, value);
    }

    public Object closureOwner() {
        return closure;
    }

    private static final long serialVersionUID = 1L;
}
