package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Env;
import groovy.lang.Closure;
import groovy.lang.MetaClassImpl;
import java.util.List;
import org.codehaus.groovy.classgen.asm.ClosureWriter;
import org.codehaus.groovy.runtime.CurriedClosure;

/**
 * {@link Closure} whose code is CPS-transformed.
 *
 * @author Kohsuke Kawaguchi
 */
public class CpsClosure extends Closure {
    private final CpsClosureDef def;

    public CpsClosure(Object owner, Object thisObject, List<String> parameters, Block body, Env capture) {
        super(owner, thisObject);
        this.def = new CpsClosureDef(parameters, body, capture, this);
    }

    /*package*/ void setParameterTypes(List<Class> types) {
        parameterTypes = types.toArray(new Class[types.size()]);
        maximumNumberOfParameters = types.size();
    }

    // returning CpsCallable lets the caller know that it needs to do CPS evaluation of this closure.
    @Override
    public Object call() {
        throw new CpsCallableInvocation("call", def, this);
    }

    @Override
    public Object call(Object... args) {
        throw new CpsCallableInvocation("call", def, this, args);
    }

    @Override
    public Object call(Object arguments) {
        throw new CpsCallableInvocation("call", def, this, arguments);
    }

    /**
     * {@link ClosureWriter} generates this function with actual argument types.
     * Here we approximate by using varargs.
     * <p>
     * {@link CurriedClosure} invokes this method directly (via {@link MetaClassImpl#invokeMethod(Class, Object, String, Object[], boolean, boolean)}
     */
    public Object doCall(Object... args) {
        throw new CpsCallableInvocation("call", def, this, args);
    }

    private static final long serialVersionUID = 1L;
}
