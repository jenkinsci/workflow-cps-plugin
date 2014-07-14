package com.cloudbees.groovy.cps.sandbox;

import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;

/**
 * @author Kohsuke Kawaguchi
 */
public class DefaultInvoker implements Invoker {
    public Object methodCall(Object receiver, boolean safe, boolean spread, String method, Object[] args) throws Throwable {
        assert !safe : "TODO";
        assert !spread : "TODO";

        CallSite callSite = fakeCallSite(method);
        Object v = callSite.call(receiver,args);
        return v;
    }

    public Object getProperty(Object lhs, boolean safe, boolean spread, String name) throws Throwable {
        assert !safe : "TODO";
        assert !spread : "TODO";

        Object v = ScriptBytecodeAdapter.getProperty(null/*Groovy doesn't use this parameter*/, lhs, name);
        return v;
    }

    public void setProperty(Object lhs, String name, boolean safe, boolean spread, Object value) throws Throwable {
        assert !safe : "TODO";
        assert !spread : "TODO";

        ScriptBytecodeAdapter.setProperty(value, null/*Groovy doesn't use this parameter*/, lhs, name);
    }

    public Object getAttribute(Object lhs, boolean safe, boolean spread, String name) throws Throwable {
        assert !safe : "TODO";
        assert !spread : "TODO";

        Object v = ScriptBytecodeAdapter.getField(null/*Groovy doesn't use this parameter*/, lhs, name);
        return v;
    }

    public void setAttribute(Object lhs, String name, boolean safe, boolean spread, Object value) throws Throwable {
        assert !safe : "TODO";
        assert !spread : "TODO";

        ScriptBytecodeAdapter.setField(value, null/*Groovy doesn't use this parameter*/, lhs, name);
    }

    /*TODO: specify the proper owner value (to the script that includes the call site) */
    protected CallSite fakeCallSite(String method) {
        CallSiteArray csa = new CallSiteArray(DefaultInvoker.class, new String[]{method});
        return csa.array[0];
    }
}
