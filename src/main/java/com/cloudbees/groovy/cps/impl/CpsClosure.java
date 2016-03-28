package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Env;
import groovy.lang.Closure;

import java.util.List;

/**
 * {@link Closure} whose code is CPS-transformed.
 *
 * @author Kohsuke Kawaguchi
 */
public class CpsClosure extends Closure {
    private final CpsClosureDef def;

    public CpsClosure(Object owner, Object thisObject, List<String> parameters, Block body, Env capture) {
        super(owner, thisObject);
        this.def = new CpsClosureDef(parameters,body,capture,this);
    }

    /*package*/ void setParameterTypes(List<Class> types) {
        parameterTypes = types.toArray(new Class[types.size()]);
        maximumNumberOfParameters = types.size();
    }

    // returning CpsCallable lets the caller know that it needs to do CPS evaluation of this closure.
    @Override
    public Object call() {
        throw new CpsCallableInvocation(def,this);
    }

    @Override
    public Object call(Object... args) {
        throw new CpsCallableInvocation(def,this,args);
    }

    @Override
    public Object call(Object arguments) {
        throw new CpsCallableInvocation(def,this,arguments);
    }

    private static final long serialVersionUID = 1L;
}
