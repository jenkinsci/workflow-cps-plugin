package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link Env} for evaluating the body of a closure.
 *
 * @author Kohsuke Kawaguchi
 */
class ClosureCallEnv extends CallEnv {
    Map<String,Object> locals;

    final CpsClosure closure;

    /**
     * Environment captured at the closure instantiation.
     */
    final Env captured;

    public ClosureCallEnv(Env caller, Continuation returnAddress, SourceLocation loc, Env captured, CpsClosure closure) {
        this(caller, returnAddress, loc, captured, closure, 0);
    }

    public ClosureCallEnv(Env caller, Continuation returnAddress, SourceLocation loc, Env captured, CpsClosure closure, int localsSize) {
        super(caller,returnAddress,loc, localsSize);
        this.closure = closure;
        this.captured = captured;
        if (localsSize <= 0) {
            locals = Collections.emptyMap();
        } else {
            locals = new HashMap<String, Object>(localsSize);
        }
    }

    public void declareVariable(Class type, String name) {
        if (locals == Collections.EMPTY_MAP) {
            locals = new HashMap<String, Object>(1);
        }
        locals.put(name, null);
        getTypesForMutation().put(name, type);
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

    public Class getLocalVariableType(String name) {
        return (locals.containsKey(name)) ? getTypes().get(name) : captured.getLocalVariableType(name);
    }

    public Object closureOwner() {
        return closure;
    }

    private static final long serialVersionUID = 1L;
}
