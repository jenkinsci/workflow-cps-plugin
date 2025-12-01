package com.cloudbees.groovy.cps.impl;

import static com.cloudbees.groovy.cps.impl.SourceLocation.*;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.sandbox.Invoker;
import edu.umd.cs.findbugs.annotations.CheckReturnValue;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.ListWithDefault;
import groovy.lang.MapWithDefault;
import groovy.lang.MetaClassImpl;
import groovy.lang.Script;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.codehaus.groovy.runtime.InvokerInvocationException;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.codehaus.groovy.runtime.callsite.CallSite;

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
        return new Next(exp, e, ptr.bind(this));
    }

    public Next then(Block exp, Env e, Continuation k) {
        return new Next(exp, e, k);
    }

    /*TODO: specify the proper owner value (to the script that includes the call site) */
    protected Next methodCall(
            Env e,
            SourceLocation loc,
            ContinuationPtr k,
            CallSiteBlock callSite,
            Object receiver,
            String methodName,
            Object... args) {
        return methodCall(e, loc, k.bind(this), callSite, receiver, methodName, args);
    }

    /**
     * Evaluates a function (possibly a workflow function), then pass the result to the given continuation.
     * @see MetaClassImpl#invokePropertyOrMissing
     * @see GroovyShell#evaluate(GroovyCodeSource)
     * @see CpsBooleanClosureWrapper#callForMap
     * @see ListWithDefault#get
     * @see MapWithDefault#get
     */
    protected Next methodCall(
            final Env e,
            final SourceLocation loc,
            final Continuation k,
            final CallSiteBlock callSite,
            final Object receiver,
            final String methodName,
            final Object... args) {
        List<String> expectedMethodNames = new ArrayList<>(2);
        expectedMethodNames.add(methodName);
        boolean laxCall = false;
        Object effectiveReceiver = findEffectiveReceiver(receiver, null);
        try {
            Caller.record(receiver, methodName, args);

            Invoker inv = e.getInvoker().contextualize(callSite);
            Object v;

            if (receiver instanceof Super) {
                Super s = (Super) receiver;
                v = inv.superCall(s.senderType, s.receiver, methodName, args);
            } else {
                if (effectiveReceiver instanceof Script) {
                    if (methodName.equals("evaluate")) { // Script.evaluate → GroovyShell.evaluate → Script.run
                        expectedMethodNames.add("run");
                    }
                    // CpsScript.invokeMethod e.g. on a UserDefinedGlobalVariable cannot be predicted from here.
                    expectedMethodNames.add("call");
                    laxCall = !((Script) effectiveReceiver)
                            .getBinding()
                            .getVariables()
                            .containsKey(methodName); // lax unless like invokePropertyOrMissing
                } else if (effectiveReceiver instanceof GroovyShell && methodName.equals("evaluate")) {
                    expectedMethodNames.add("run");
                } else if (effectiveReceiver instanceof CpsBooleanClosureWrapper && methodName.equals("callForMap")) {
                    expectedMethodNames.add("call");
                } else if ((effectiveReceiver instanceof ListWithDefault || effectiveReceiver instanceof MapWithDefault)
                        && methodName.equals("get")) {
                    expectedMethodNames.add("call");
                }
                // TODO: spread
                v = inv.methodCall(receiver, methodName, args);
            }
            // if this was a normal function, the method had just executed synchronously
            return k.receive(v);
        } catch (CpsCallableInvocation inv) {
            if (!methodName.startsWith("$")) { // see TODO comment in Translator w.r.t. overloadsResolved
                if (laxCall && inv.receiver instanceof CpsClosure) {
                    // Potential false negative from overly lax addition above.
                    expectedMethodNames.remove("call");
                }
                inv.checkMismatch(effectiveReceiver, expectedMethodNames);
            }
            return inv.invoke(e, loc, k);
        } catch (Throwable t) {
            return throwException(e, t, loc, new ReferenceStackTrace());
        }
    }

    private static Object findEffectiveReceiver(Object receiver, Map<Object, Boolean> encountered) {
        if (!(receiver instanceof CpsClosure)) {
            return receiver;
        }
        if (encountered == null) {
            encountered = new IdentityHashMap<>();
        }
        if (encountered.put(receiver, true) == null) {
            return findEffectiveReceiver(((CpsClosure) receiver).getOwner(), encountered);
        } else {
            return receiver;
        }
    }

    /**
     * Fix up the stack trace of an exception thrown from synchronous code.
     */
    private void fixupStackTrace(Env e, Throwable t, SourceLocation loc, ReferenceStackTrace ref) {
        StackTraceElement[] rs = ref.getStackTrace();
        StackTraceElement[] ts = t.getStackTrace();

        if (!hasSameRoots(rs, ts)) {
            // this exception doesn't match up with what we expected.
            // maybe it was created elsewhere and thrown here?
            return;
        }

        /*
           SYNC TRACE
             this section contains the top portion of the actual stack beyond ReferenceStackTrace that led to the
             instantiation of the exception. This is the synchronous code called from CPS code that created the exception.
           CPS TRACE
             this section contains a synthesized fake stack trace that shows the logical stack trace of the CPS code
           REFERENCE TRACE
             this section contains the actual stack trace leading up to the point where ReferenceStackTrace is created
             to show how the actual execution happened on JVM
        */

        List<StackTraceElement> orig = List.of(ts);
        int pos = ts.length - rs.length;
        List<StackTraceElement> stack = new ArrayList<>(orig.subList(0, pos));

        stack.add((loc != null ? loc : UNKNOWN).toStackTrace());
        e.buildStackTraceElements(stack, Integer.MAX_VALUE);
        stack.add(Continuable.SEPARATOR_STACK_ELEMENT);

        stack.addAll(orig.subList(pos, orig.size()));

        t.setStackTrace(stack.toArray(new StackTraceElement[stack.size()]));
    }

    /**
     * Returns true if 'rs' is at the bottom of 'ts'.
     */
    private boolean hasSameRoots(StackTraceElement[] rs, StackTraceElement[] ts) {
        int b = ts.length - rs.length;
        if (b < 0) return false;

        { // the top of the stack will have different line number because ReferenceStackTrace is created in a separate
            // line
            StackTraceElement lhs = ts[b];
            StackTraceElement rhs = rs[0];

            if (!eq(lhs.getClassName(), rhs.getClassName())
                    || !eq(lhs.getMethodName(), rhs.getMethodName())
                    || !eq(lhs.getFileName(), rhs.getFileName())) return false;
        }

        for (int i = 1; i < rs.length; i++) {
            if (!ts[b + i].equals(rs[i])) return false;
        }

        return true;
    }

    private boolean eq(Object x, Object y) {
        if (x == y) return true;
        if (x == null || y == null) return false;
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
        fixupStackTrace(e, t, loc, ref);
        return e.getExceptionHandler(t.getClass()).receive(t);
    }

    /**
     * Casts the result of the given value to a Boolean using {@link Invoker#cast}.
     *
     * @param value
     *      The value to cast
     * @param e
     *      {@link Env} whose {@link Invoker} will be used to perform the cast
     * @param fn
     *      The {@link Next}-returning function that the resulting boolean will be applied to
     */
    protected Next castToBoolean(Object value, Env e, Function<Boolean, Next> fn) {
        try {
            Object result = e.getInvoker().cast(value, Boolean.class, false, false, false);
            // Invoker.cast with coerce=false uses DefaultTypeTransformation.castToType, which may return null, as
            // opposed to DefaultTypeTransformation.castToBoolean which we are trying to mimic.
            boolean b = Boolean.TRUE.equals(result);
            return fn.apply(b);
        } catch (Throwable t) {
            // It should not be possible to receive a top-level CpsCallableInvocation here.
            if (t instanceof InvokerInvocationException) {
                // DefaultTypeTransformation calls asBoolean via InvokerHelper, which wraps all thrown exceptions
                // in InvokerInvocationException. CpsCallableInvocation in this context has always resulted in
                // "Unexpected exception in CPS VM thread", so there is no need to attempt to recover by invoking
                // the callable.
                Throwable cause = t.getCause();
                if (cause instanceof CpsCallableInvocation) {
                    CpsCallableInvocation inv = (CpsCallableInvocation) cause;
                    inv.checkMismatch(ScriptBytecodeAdapter.class, List.of("castToType"));
                    String classAndMethod = inv.getClassAndMethodForDisplay();
                    t = new IllegalStateException(classAndMethod
                            + " must be @NonCPS; see: https://jenkins.io/redirect/pipeline-cps-method-mismatches/");
                }
            }
            return throwException(e, t, null, new ReferenceStackTrace());
        }
    }

    private static final long serialVersionUID = 1L;
}
