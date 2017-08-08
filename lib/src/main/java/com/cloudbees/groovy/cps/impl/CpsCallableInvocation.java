package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

import java.util.List;

import static java.util.Arrays.*;
import java.util.Collections;

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
    public final CpsCallable call;
    public final Object receiver;
    public final List arguments;

    public CpsCallableInvocation(CpsCallable call, Object receiver, Object... arguments) {
        this.call = call;
        this.receiver = receiver;
        this.arguments = arguments != null ? asList(arguments) : Collections.emptyList();
    }

    public Next invoke(Env caller, SourceLocation loc, Continuation k) {
        return call.invoke(caller, loc, receiver,arguments,k);
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

}
