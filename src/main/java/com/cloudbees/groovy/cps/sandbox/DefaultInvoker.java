package com.cloudbees.groovy.cps.sandbox;

import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;

/**
 * @author Kohsuke Kawaguchi
 */
public class DefaultInvoker implements Invoker {
    public Object methodCall(Object receiver, boolean safe, boolean spread, String method, Object[] args) throws Throwable {
        CallSite callSite = fakeCallSite(method);
        Object v = callSite.call(receiver,args);
        return v;
    }

    /*TODO: specify the proper owner value (to the script that includes the call site) */
    protected CallSite fakeCallSite(String method) {
        CallSiteArray csa = new CallSiteArray(DefaultInvoker.class, new String[]{method});
        return csa.array[0];
    }
}
