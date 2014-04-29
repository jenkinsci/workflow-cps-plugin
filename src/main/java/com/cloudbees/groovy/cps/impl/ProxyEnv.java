package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class ProxyEnv implements Env {
    protected final Env parent;

    public ProxyEnv(Env parent) {
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

    public Object closureOwner() {
        return parent.closureOwner();
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

    public void buildStackTraceElements(List<StackTraceElement> stack) {
        parent.buildStackTraceElements(stack);
    }

    private static final long serialVersionUID = 1L;
}
