package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.SourceLocation;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import groovy.lang.Script;

import java.io.Serializable;

/**
 * Combines two {@link Continuation}s into one.
 *
 * Essentially, take two functions 'first', and 'then', and creates a new function
 *
 * <pre>
 * concat(x) := then(first(x))
 * </pre>
 *
 * <p>
 * Note that this is useful but expensive. If you control how the 'first' Continuation
 * gets created, you should try to have 'then' incorporated into it during its creation,
 * such as {@link Continuable#Continuable(Script, Env, Continuation)} or via
 * {@link CpsCallableInvocation#invoke(Env, SourceLocation, Continuation)}.
 *
 * @author Kohsuke Kawaguchi
 */
public class ConcatenatedContinuation implements Continuation {
    private final Continuation first;
    private final Function<Outcome,Outcome> f;
    private final Continuable then;

    public ConcatenatedContinuation(Continuation first, Function<Outcome,Outcome> mapper, Continuable then) {
        this.first = first;
        this.f = mapper;
        this.then = then;
    }

    public ConcatenatedContinuation(Continuation first, Continuable then) {
        this(first, Idem.INSTANCE, then);
    }

    public Next receive(Object o) {
        Next n = first.receive(o);

        ConcatenatedContinuation concat = new ConcatenatedContinuation(n.k, f, then);

        if (n.yield!=null) {
            if (n.k==Continuation.HALT) {
                // the first part is done
                Outcome out = f.apply(n.yield);
                return out.resumeFrom(then);
            }
            return new Next(n.e, concat, n.yield);
        } else                    return new Next(n.f, n.e, concat);
    }

    private static final long serialVersionUID = 1L;

    private static final class Idem<T> implements Function<T,T>, Serializable {
        public T apply(T input) {
            return input;
        }

        private Object readResolve() {
            return INSTANCE;
        }

        private static final long serialVersionUID = 1L;
        private static final Idem INSTANCE = new Idem();
    }
}
