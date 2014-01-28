package com.cloudbees.groovy.cps;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
class FunctionCallEnv implements Env {
    // TODO: How do we correctly assign local variables to its scope?

    // TODO: delegate?
    final Map<String,Object> locals = new HashMap<String, Object>();

    private final Continuation returnAddress;

    FunctionCallEnv(Object _this, Continuation returnAddress) {
        locals.put("this",_this);
        this.returnAddress = returnAddress;
    }

    public void declareVariable(String name) {
        // no-op
    }

    public Object getLocalVariable(String name) {
        return locals.get(name);
    }

    public void setLocalVariable(String name, Object value) {
        locals.put(name,value);
    }

    public Continuation getReturnAddress() {
        return returnAddress;
    }
}
