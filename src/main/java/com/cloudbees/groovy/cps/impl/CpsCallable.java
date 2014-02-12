package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
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
     *      This parameter is meaningless for closures because it's not invoked with a LHS object.
     * @param args
     *      Arguments to the call that match up with {@link #parameters}.
     * @param k
     *      The result of the function/closure call will be eventually passed to this continuation.
     */
    abstract Next invoke(Env caller, Object receiver, List<?> args, Continuation k);

    protected final void assignArguments(List<?> args, Env e) {
        assert args.size()== parameters.size();  // TODO: varargs
        for (int i=0; i< parameters.size(); i++) {
            e.setLocalVariable(parameters.get(i), args.get(i));
        }
    }
}
