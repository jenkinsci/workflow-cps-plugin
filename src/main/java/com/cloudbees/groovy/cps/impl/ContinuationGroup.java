package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.CpsFunction;
import com.cloudbees.groovy.cps.Next;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;

import java.util.Arrays;

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

    protected Next methodCall(Env e, ContinuationPtr k, Object receiver, String methodName, Object... args) {
        return methodCall(e,k.bind(this),receiver,methodName,args);
    }

    /**
     * Evaluates a function (possibly a workflow function), then pass the result to the given continuation.
     */
    protected Next methodCall(Env e, Continuation k, Object receiver, String methodName, Object... args) {
        Object v;
        try {
            CallSite callSite = fakeCallSite(methodName);
            v = callSite.call(receiver,args);
        } catch (Throwable t) {
            return throwException(e, t);
        }

        if (v instanceof CpsFunction) {
            // if this is a workflow function, it'd return a CpsFunction object instead
            // of actually executing the function, so execute it in the CPS
            return ((CpsFunction)v).invoke(e, receiver, Arrays.asList(args), k);
        } else {
            // if this was a normal function, the method had just executed synchronously
            return k.receive(v);
        }
    }

    /**
     * Throws an exception into the CPS code by finding a suitable exception handler
     * and resuming the execution from that point.
     *
     * We use this method to receive an exception thrown from the normal code and "rethrow"
     * into the CPS code.
     */
    protected Next throwException(Env e, Throwable t) {
        return e.getExceptionHandler(t.getClass()).receive(t);
    }
}
