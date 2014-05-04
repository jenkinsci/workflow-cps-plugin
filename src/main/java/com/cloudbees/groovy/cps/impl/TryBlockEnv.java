package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * @author Kohsuke Kawaguchi
 */
class TryBlockEnv extends ProxyEnv {
    private final Map<Class,Continuation> handlers = new LinkedHashMap<Class, Continuation>();

    public TryBlockEnv(Env parent) {
        super(parent);
    }

    /**
     * Handlers can be only added immediately after instantiation.
     */
    public void addHandler(Class<? extends Throwable> type, Continuation k) {
        handlers.put(type,k);
    }

    @Override
    public Continuation getExceptionHandler(Class<? extends Throwable> type) {
        for (Entry<Class, Continuation> e : handlers.entrySet()) {
            if (e.getKey().isAssignableFrom(type))
                return e.getValue();
        }

        return super.getExceptionHandler(type);
    }

    private static final long serialVersionUID = 1L;
}
