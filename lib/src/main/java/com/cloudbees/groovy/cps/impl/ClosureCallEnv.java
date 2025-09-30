package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.google.common.collect.Maps;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link Env} for evaluating the body of a closure.
 *
 * @author Kohsuke Kawaguchi
 */
class ClosureCallEnv extends CallEnv {
    /** Lazily declared using {@link Collections#EMPTY_MAP} until we declare variables, then converted to a (small) {@link HashMap} */
    /** To conserve memory, lazily declared using {@link Collections#EMPTY_MAP} until we declare variables, then converted to a (small) {@link HashMap} */
    Map<String, Object> locals;

    final CpsClosure closure;

    /**
     * Environment captured at the closure instantiation.
     */
    final Env captured;

    public ClosureCallEnv(
            Env caller, Continuation returnAddress, SourceLocation loc, Env captured, CpsClosure closure) {
        this(caller, returnAddress, loc, captured, closure, 0);
    }

    public ClosureCallEnv(
            Env caller,
            Continuation returnAddress,
            SourceLocation loc,
            Env captured,
            CpsClosure closure,
            int localsSize) {
        super(caller, returnAddress, loc, localsSize);
        this.closure = closure;
        this.captured = captured;
        if (localsSize <= 0) {
            locals = Collections.EMPTY_MAP;
        } else {
            locals = Maps.newHashMapWithExpectedSize(localsSize);
        }
    }

    public void declareVariable(Class type, String name) {
        if (locals == Collections.EMPTY_MAP) {
            locals = new HashMap<>(2);
        }
        locals.put(name, null);
        getTypesForMutation().put(name, type);
    }

    public Object getLocalVariable(String name) {
        if (locals.containsKey(name)) return locals.get(name);
        else return captured.getLocalVariable(name);
    }

    public void setLocalVariable(String name, Object value) {
        if (locals.containsKey(name)) locals.put(name, value);
        else captured.setLocalVariable(name, value);
    }

    public Class getLocalVariableType(String name) {
        return (locals.containsKey(name)) ? getTypes().get(name) : captured.getLocalVariableType(name);
    }

    public Object closureOwner() {
        return closure;
    }

    private static final long serialVersionUID = 1L;
}
