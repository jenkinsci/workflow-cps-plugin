package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

import java.util.List;

import static java.util.Arrays.*;
import java.util.Collections;
import javax.annotation.CheckForNull;

/**
 * When an CPS-interpreted method is invoked, it immediately throws this error
 * to signal that the method execution needs to be interpreted.
 *
 * <p>
 * The instance captures everything necessary to invoke a function,
 * which is
 * {@linkplain #call the definition of the function},
 * {@linkplain #receiver object that the function is invoked on}, and
 * {@linkplain #arguments actual arguments to the function}.
 *
 * When we invoke CPS-transformed closure or function, this throwable gets thrown.
 *
 * @author Kohsuke Kawaguchi
 */
public class CpsCallableInvocation extends Error/*not really an error but we want something that doesn't change signature*/ {
    public final String methodName;
    public final CpsCallable call;
    public final Object receiver;
    public final List arguments;

    @Deprecated
    public CpsCallableInvocation(CpsCallable call, Object receiver, Object... arguments) {
        this("?", call, receiver, arguments);
    }

    /**
     * @param methodName see {@link #invoke(String, Env, SourceLocation, Continuation)}
     */
    public CpsCallableInvocation(String methodName, CpsCallable call, Object receiver, Object... arguments) {
        this.methodName = methodName;
        this.call = call;
        this.receiver = receiver;
        this.arguments = arguments != null ? asList(arguments) : Collections.emptyList();
    }

    public Next invoke(Env caller, SourceLocation loc, Continuation k) {
        return call.invoke(caller, loc, receiver,arguments,k);
    }

    /**
     * @param expectedMethodName when not matching that passed to {@link CpsCallableInvocation#CpsCallableInvocation(String, CpsCallable, Object, Object...)}, will use logic from {@link #registerMismatchHandler}
     */
    public Next invoke(String expectedMethodName, Env caller, SourceLocation loc, Continuation k) {
        if (isMismatch(expectedMethodName, methodName)) {
            MismatchHandler handler = handlers.get();
            if (handler != null) {
                handler.handle(expectedMethodName, methodName);
            }
        }
        return invoke(caller, loc, k);
    }

    /** @see #registerMismatchHandler */
    @FunctionalInterface
    public interface MismatchHandler {
        void handle(String expectedMethodName, String actualMethodName);
    }

    private static final ThreadLocal<MismatchHandler> handlers = new ThreadLocal<>();

    /** @see #invoke(String, Env, SourceLocation, Continuation) */
    public static void registerMismatchHandler(@CheckForNull MismatchHandler handler) {
        handlers.set(handler);
    }
    
    /**
     * Creates a {@link Block} that performs this invocation and pass the result to the given {@link Continuation}.
     */
    public Block asBlock() {
        return new Block() {
            public Next eval(Env e, Continuation k) {
                return invoke(e, null, k);
            }
        };
    }

    @Override
    public synchronized Throwable fillInStackTrace() {
        return this;
    }

    @Override
    public String toString() {
        return "CpsCallableInvocation{methodName=" + methodName + ", call=" + call + ", receiver=" + receiver + ", arguments=" + arguments + '}';
    }

    /** @see <a href="https://issues.jenkins-ci.org/browse/JENKINS-31314">JENKINS-31314</a> */
    private static boolean isMismatch(String expected, String caught) {
        if (expected.equals(caught)) {
            return false;
        }
        if (expected.startsWith("$")) {
            // see TODO comment in Translator w.r.t. overloadsResolved
            return false;
        }
        if (expected.equals("evaluate") && caught.equals("run")) {
            return false;
        }
        return true;
    }

}
