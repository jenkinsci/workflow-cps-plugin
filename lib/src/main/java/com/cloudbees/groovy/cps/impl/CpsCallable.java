package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import com.google.common.collect.ImmutableList;
import java.io.Serializable;
import java.util.List;

/**
 * Common part of {@link CpsFunction} and {@link CpsClosureDef}, which represents something that's invokable.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class CpsCallable implements Serializable {
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
     * @param loc
     *      Source location of the call site. Used to build stack trace elements. Null if the call
     *      happens outside the CPS transformed world (think of the call into {@link Thread#run()}
     *      in Java, which cannot be explained within the Java semantics.)
     * @param receiver
     *      For functions, this is the left hand side of the expression that becomes 'this' object.
     *      This parameter is meaningless for closures because it's not invoked with a LHS object.
     * @param args
     *      Arguments to the call that match up with {@link #parameters}.
     * @param k
     */
    abstract Next invoke(Env caller, SourceLocation loc, Object receiver, List<?> args, Continuation k);

    protected final void assignArguments(List<?> args, Env e) {
        // TODO: var args
        for (int i = 0; i < Math.min(args.size(), parameters.size()); i++) {
            e.declareVariable(Object.class, parameters.get(i));
            e.setLocalVariable(parameters.get(i), args.get(i));
        }
    }

    private static final long serialVersionUID = 1L;
}
