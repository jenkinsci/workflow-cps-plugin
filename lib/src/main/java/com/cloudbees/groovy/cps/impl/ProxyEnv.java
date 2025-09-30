package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.DepthTrackingEnv;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.sandbox.Invoker;
import java.util.List;

/**
 * {@link Env} that delegates to another {@link Env}.
 *
 * Useful base class for {@link Env} impls.
 *
 * @author Kohsuke Kawaguchi
 */
public class ProxyEnv implements DepthTrackingEnv {
    protected final Env parent;

    int depth = 0;

    public ProxyEnv(Env parent) {
        this.parent = parent;
        depth = (parent instanceof DepthTrackingEnv) ? ((DepthTrackingEnv) parent).getDepth() : 0;
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

    public Class getLocalVariableType(String name) {
        return parent.getLocalVariableType(name);
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

    public Invoker getInvoker() {
        return parent.getInvoker();
    }

    public void buildStackTraceElements(List<StackTraceElement> stack, int depth) {
        parent.buildStackTraceElements(stack, depth);
    }

    private static final long serialVersionUID = 1L;

    @Override
    public int getDepth() {
        return depth;
    }
}
