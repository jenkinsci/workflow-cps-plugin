package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;

/**
 * @author Kohsuke Kawaguchi
 */
abstract class ProxyEnv implements Env {
    protected final Env parent;

    protected ProxyEnv(Env parent) {
        this.parent = parent;
    }

    public void declareVariable(Class type, String name) {
        parent.declareVariable(type, name);
    }

    public Object getLocalVariable(String name) {
        return parent.getLocalVariable(name);
    }

    public void setLocalVariable(String name, Object value) {
        parent.setLocalVariable(name, value);
    }

    public Continuation getReturnAddress() {
        return parent.getReturnAddress();
    }

    public Continuation getBreakAddress(String label) {
        return parent.getBreakAddress(label);
    }

    public Continuation getContinueAddress(String label) {
        return parent.getContinueAddress(label);
    }

    public Continuation getExceptionHandler(Class<? extends Throwable> type) {
        return parent.getExceptionHandler(type);
    }
}
