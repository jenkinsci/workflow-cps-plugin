package com.cloudbees.groovy.cps.sandbox;

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
        Object v = callSite.call(receiver,args);
        return v;
    }

    public Object constructorCall(Class lhs, Object[] args) throws Throwable {
        Object v = fakeCallSite("<init>").callConstructor(lhs,args);
        return v;
    }

    public Object getProperty(Object lhs, String name) throws Throwable {
        Object v = ScriptBytecodeAdapter.getProperty(null/*Groovy doesn't use this parameter*/, lhs, name);
        return v;
    }

    public void setProperty(Object lhs, String name, Object value) throws Throwable {
        ScriptBytecodeAdapter.setProperty(value, null/*Groovy doesn't use this parameter*/, lhs, name);
    }

    public Object getAttribute(Object lhs, String name) throws Throwable {
        Object v = ScriptBytecodeAdapter.getField(null/*Groovy doesn't use this parameter*/, lhs, name);
        return v;
    }

    public void setAttribute(Object lhs, String name, Object value) throws Throwable {
        ScriptBytecodeAdapter.setField(value, null/*Groovy doesn't use this parameter*/, lhs, name);
    }

    public Object getArray(Object lhs, Object index) throws Throwable {
        return fakeCallSite("getAt").call(lhs,index);
    }

    public void setArray(Object lhs, Object index, Object value) throws Throwable {
        fakeCallSite("putAt").call(lhs,index,value);
    }

    /*TODO: specify the proper owner value (to the script that includes the call site) */
    protected CallSite fakeCallSite(String method) {
        CallSiteArray csa = new CallSiteArray(DefaultInvoker.class, new String[]{method});
        return csa.array[0];
    }
}
