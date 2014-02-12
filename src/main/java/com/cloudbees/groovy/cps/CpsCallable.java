package com.cloudbees.groovy.cps;

import com.google.common.collect.ImmutableList;
import groovy.lang.Closure;

import java.util.List;

/**
 * Common part of {@link CpsFunction} and {@link CpsClosureDef}, which represents something that's invokable.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class CpsCallable {
    final Block body;
    final ImmutableList<String> parameters;

    /*package*/ CpsCallable(List<String> parameters, Block body) {
        this.body = body;
        this.parameters = ImmutableList.copyOf(parameters);
    }

    /**
     * Invokes this callable something.
     *
     * @param caller
     *      Environment of the caller. For example, if this invokable object throws an exception,
     *      we might have to use this environment to find where to dispatch that exception.
     * @param receiver
     *      For functions, this is the left hand side of the expression that becomes 'this' object.
     *      For closures, this is the {@link Closure} object itself.
     * @param args
     *      Arguments to the call that match up with {@link #parameters}.
     * @param k
     *      The result of the function/closure call will be eventually passed to this continuation.
     */
    abstract Next invoke(Env caller, Object receiver, List<?> args, Continuation k);
}
