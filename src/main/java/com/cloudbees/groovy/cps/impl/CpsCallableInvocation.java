package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

import java.util.List;

import static java.util.Arrays.*;

/**
 * Invocation of {@link CpsCallable}.
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
        this.arguments = asList(arguments);
    }

    public Next invoke(Env caller, Continuation k) {
        return call.invoke(caller,receiver,arguments,k);
    }

    /**
     * Creates a {@link Block} that performs this invocation and pass the result to the given {@link Continuation}.
     */
    public Block asBlock() {
        return new Block() {
            public Next eval(Env e, Continuation k) {
                return invoke(e,k);
            }
        };
    }
}
