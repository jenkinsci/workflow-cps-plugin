package com.cloudbees.groovy.cps;

/**
 * @author Kohsuke Kawaguchi
 */
abstract class ProxyEnv implements Env {
    protected final Env parent;

    protected ProxyEnv(Env parent) {
        this.parent = parent;
    }

    public void declareVariable(String name) {
        parent.declareVariable(name);
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
}
