package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.codehaus.groovy.runtime.callsite.CallSite;

import javax.annotation.CheckReturnValue;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.cloudbees.groovy.cps.impl.SourceLocation.*;

/**
 * Base class for defining a series of {@link Continuation} methods that share the same set of contextual values.
 *
 * Subtypes are expected to define a number of methods that have the same signature as {@link Continuation#receive(Object)}.
 * These methods can be wrapped into a {@link Continuation} instance via {@link #then(Block, Env, ContinuationPtr)} method.
 *
 * @see ContinuationPtr
 * @author Kohsuke Kawaguchi
 */
abstract class ContinuationGroup implements Serializable {
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
    protected Next methodCall(Env e, SourceLocation loc, ContinuationPtr k, Object receiver, String methodName, Object... args) {
        return methodCall(e,loc,k.bind(this),receiver,methodName,args);
    }

    /**
     * Evaluates a function (possibly a workflow function), then pass the result to the given continuation.
     */
    protected Next methodCall(final Env e, final SourceLocation loc, final Continuation k, final Object receiver, final String methodName, final Object... args) {
        try {
            // TODO: spread and safe
            Object v = e.getInvoker().methodCall(receiver, methodName, args);
            // if this was a normal function, the method had just executed synchronously
            return k.receive(v);
        } catch (CpsCallableInvocation inv) {
            return inv.invoke(e, loc, k);
        } catch (Throwable t) {
            return throwException(e, t, loc, new ReferenceStackTrace());
        }
    }

    /**
     * Fix up the stack trace of an exception thrown from synchronous code.
     */
    private void fixupStackTrace(Env e, Throwable t, SourceLocation loc, ReferenceStackTrace ref) {
        StackTraceElement[] rs = ref.getStackTrace();
        StackTraceElement[] ts = t.getStackTrace();

        if (!hasSameRoots(rs,ts)) {
            // this exception doesn't match up with what we expected.
            // maybe it was created elsewhere and thrown here?
            return;
        }

        /*
            SYNC TRACE
            CPS TRACE
            REFERENECE TRACE
         */

        List<StackTraceElement> orig = Arrays.asList(ts);
        int pos = ts.length-rs.length;
        List<StackTraceElement> stack = new ArrayList<StackTraceElement>(orig.subList(0,pos));

        stack.add((loc!=null ? loc : UNKNOWN).toStackTrace());
        e.buildStackTraceElements(stack,Integer.MAX_VALUE);
        stack.add(Continuable.SEPARATOR_STACK_ELEMENT);

        stack.addAll(orig.subList(pos, orig.size()));

        t.setStackTrace(stack.toArray(new StackTraceElement[stack.size()]));
    }

    /**
     * Returns true if 'rs' is at the bottom of 'ts'.
     */
    private boolean hasSameRoots(StackTraceElement[] rs, StackTraceElement[] ts) {
        int b = ts.length-rs.length;
        if (b<0)    return false;

        {// the top of the stack will have different line number because ReferenceStackTrace is created in a separate line
            StackTraceElement lhs = ts[b];
            StackTraceElement rhs = rs[0];

            if (!eq(lhs.getClassName(),rhs.getClassName())
             || !eq(lhs.getMethodName(),rhs.getMethodName())
             || !eq(lhs.getFileName(),rhs.getFileName()))
                return false;
        }

        for (int i=1; i<rs.length; i++) {
            if (!ts[b+i].equals(rs[i]))
                return false;
        }

        return true;
    }

    private boolean eq(Object x, Object y) {
        if (x==y)   return true;
        if (x==null || y==null) return false;
        return x.equals(y);
    }

    /**
     * Throws an exception into the CPS code by finding a suitable exception handler
     * and resuming the execution from that point.
     *
     * We use this method to receive an exception thrown from the normal code and "rethrow"
     * into the CPS code.
     *
     * @param t
     *      Exception thrown
     * @param loc
     *      Location of the call site in the script. null if unknown.
     * @param e
     *      Environment that represents the call stack of the asynchronous code
     * @param ref
     *      Reference stack trace that identifies the call site. Create this exception in the same
     *      function that you call into {@link CallSite}. Used to identify the section of {@coe t.getStackTrace()}
     *      that belong to the caller of groovy-cps and the invocation of {@link CallSite}  induced by the Groovy script.
     */
    @CheckReturnValue
    protected Next throwException(Env e, Throwable t, SourceLocation loc, ReferenceStackTrace ref) {
        fixupStackTrace(e, t,loc, ref);
        return e.getExceptionHandler(t.getClass()).receive(t);
    }

    private static final long serialVersionUID = 1L;
}
