package com.cloudbees.groovy.cps.sandbox;

import com.cloudbees.groovy.cps.impl.CallSiteBlock;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.MetaClass;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.MethodClosure;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;

/**
 * {@link Invoker} that performs the expected operation without anything extra.
 *
 * @author Kohsuke Kawaguchi
 */
public class DefaultInvoker implements Invoker {
    public Object methodCall(Object receiver, String method, Object[] args) throws Throwable {
        CallSite callSite = fakeCallSite(method);
        Object v = callSite.call(receiver, args);
        return v;
    }

    public Object constructorCall(Class lhs, Object[] args) throws Throwable {
        Object v = fakeCallSite("<init>").callConstructor(lhs, args);
        return v;
    }

    public Object superCall(Class methodType, Object receiver, String method, Object[] args) throws Throwable {
        try {
            MetaClass mc = InvokerHelper.getMetaClass(receiver.getClass());
            return mc.invokeMethod(methodType.getSuperclass(), receiver, method, args, true, true);
        } catch (GroovyRuntimeException gre) {
            throw ScriptBytecodeAdapter.unwrap(gre);
        }
    }

    public Object getProperty(Object lhs, String name) throws Throwable {
        Object v = ScriptBytecodeAdapter.getProperty(null /*Groovy doesn't use this parameter*/, lhs, name);
        return v;
    }

    public void setProperty(Object lhs, String name, Object value) throws Throwable {
        ScriptBytecodeAdapter.setProperty(value, null /*Groovy doesn't use this parameter*/, lhs, name);
    }

    public Object getAttribute(Object lhs, String name) throws Throwable {
        Object v = ScriptBytecodeAdapter.getField(null /*Groovy doesn't use this parameter*/, lhs, name);
        return v;
    }

    public void setAttribute(Object lhs, String name, Object value) throws Throwable {
        ScriptBytecodeAdapter.setField(value, null /*Groovy doesn't use this parameter*/, lhs, name);
    }

    public Object getArray(Object lhs, Object index) throws Throwable {
        return fakeCallSite("getAt").call(lhs, index);
    }

    public void setArray(Object lhs, Object index, Object value) throws Throwable {
        fakeCallSite("putAt").call(lhs, index, value);
    }

    public Object methodPointer(Object lhs, String name) {
        return new MethodClosure(lhs, name);
    }

    @Override
    public Object cast(Object value, Class<?> type, boolean ignoreAutoboxing, boolean coerce, boolean strict)
            throws Throwable {
        // TODO: What should we do with ignoreAutoboxing and strict?
        return coerce ? ScriptBytecodeAdapter.asType(value, type) : ScriptBytecodeAdapter.castToType(value, type);
    }

    public Invoker contextualize(CallSiteBlock tags) {
        return this;
    }

    /*TODO: specify the proper owner value (to the script that includes the call site) */
    protected CallSite fakeCallSite(String method) {
        CallSiteArray csa = new CallSiteArray(DefaultInvoker.class, new String[] {method});
        return csa.array[0];
    }

    private static final long serialVersionUID = 1L;
}
