package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Function;
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

    /**
     * Evaluates a function (possibly a workflow function), then pass the result to the continuation
     * represented by {@link ContinuationPtr} on this instance.
     */
    protected Next methodCall(Env e, ContinuationPtr k, Object receiver, String methodName, Object... args) {
        Object v;
        try {
            CallSite callSite = fakeCallSite(methodName);
            v = callSite.call(receiver,args);
        } catch (Throwable t) {
            throw new UnsupportedOperationException(t);     // TODO: exception handling
        }

        if (v instanceof Function) {
            // if this is a workflow function, it'd return a Function object instead
            // of actually executing the function, so execute it in the CPS
            return ((Function)v).invoke(e, receiver, Arrays.asList(args), k.bind(this));
        } else {
            // if this was a normal function, the method had just executed synchronously
            return k.bind(this).receive(v);
        }
    }
}
