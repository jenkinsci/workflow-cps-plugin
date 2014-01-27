package com.cloudbees.groovy.cps;

import java.util.HashMap;
import java.util.Map;

/**
 * New stack frame created when calling a function.
 */
class EnvImpl implements Env {
    // TODO: delegate?
    final Map<String,Object> locals = new HashMap<String, Object>();

    EnvImpl(Object _this) {
        locals.put("this",_this);
    }

    public Function resolveFunction(String name) {
        // TODO:
        return null;
    }

    public Object get(String name) {
        return locals.get(name);
    }

    public void set(String name, Object value) {
        locals.put(name,value);
    }

    public Env newBlockScope() {
        // TODO
        return this;
    }
}
