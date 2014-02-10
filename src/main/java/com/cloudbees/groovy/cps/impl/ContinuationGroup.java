package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;

/**
 * Base class for defining a series of {@link Continuation} methods that share the same set of contextual values.
 *
 * Subtypes are expected to define a number of methods that have the same signature as {@link Continuation#receive(Object)}.
 * These methods can be wrapped into a {@link Continuation} instance via {@link #then(Block, Env, ContinuationPtr)} method.
 *
 * @see ContinuationPtr
 * @author Kohsuke Kawaguchi
 */
abstract class ContinuationGroup {
    public Next then(Block exp, Env e, ContinuationPtr ptr) {
        return new Next(exp,e,ptr.bind(this));
    }

    public Next then(Block exp, Env e, Continuation k) {
        return new Next(exp,e,k);
    }

    /**
     * Casts the value to boolean by following the Groovy semantics.
     */
    protected final boolean asBoolean(Object o) {
        try {
            return (Boolean) ScriptBytecodeAdapter.asType(o, Boolean.class);
        } catch (Throwable e) {
            // TODO: exception handling
            e.printStackTrace();
            return false;
        }
    }

    /*TODO: specify the proper owner value (to the script that includes the call site) */
    protected static CallSite fakeCallSite(String method) {
        CallSiteArray csa = new CallSiteArray(ContinuationGroup.class, new String[]{method});
        return csa.array[0];
    }

    protected Object methodCall(Object receiver, String methodName) {
        try {
            CallSite callSite = fakeCallSite(methodName);
            return callSite.call(receiver);
        } catch (Throwable t) {
            throw new UnsupportedOperationException(t);     // TODO: exception handling
        }
    }

    protected Object methodCall(Object receiver, String methodName, Object arg1) {
        try {
            CallSite callSite = fakeCallSite(methodName);
            return callSite.call(receiver, arg1);
        } catch (Throwable t) {
            throw new UnsupportedOperationException(t);     // TODO: exception handling
        }
    }
}
