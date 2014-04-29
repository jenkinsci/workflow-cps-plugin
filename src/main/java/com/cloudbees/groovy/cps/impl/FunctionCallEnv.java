package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
// TODO: should be package local once all the impls move into this class
public class FunctionCallEnv extends CallEnv {
    // TODO: delegate?
    final Map<String,Object> locals = new HashMap<String, Object>();

    /**
     * @param caller
     *      The environment of the call site. Can be null but only if the caller is outside CPS execution.
     */
    public FunctionCallEnv(Env caller, Continuation returnAddress, SourceLocation loc, Object _this) {
        super(caller,returnAddress,loc);
        locals.put("this",_this);
    }

    public void declareVariable(Class type, String name) {
        // no-op
    }

    public Object getLocalVariable(String name) {
        return locals.get(name);
    }

    public void setLocalVariable(String name, Object value) {
        locals.put(name,value);
    }

    public Object closureOwner() {
        return getLocalVariable("this");
    }

    private static final long serialVersionUID = 1L;
}
