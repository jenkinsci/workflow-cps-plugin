package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Environment for evaluating the body of a try/catch block.
 *
 * @author Kohsuke Kawaguchi
 */
// TODO: should be package local once all the impls move into this class
public class TryBlockEnv extends ProxyEnv {
    private final Map<Class, Continuation> handlers = new LinkedHashMap<>(2);

    @CheckForNull
    private final Block finally_;

    public TryBlockEnv(Env parent, Block finally_) {
        super(parent);
        this.finally_ = finally_;
    }

    /**
     * Handlers can be only added immediately after instantiation.
     */
    public void addHandler(Class<? extends Throwable> type, Continuation k) {
        handlers.put(type, k);
    }

    @Override
    public Continuation getExceptionHandler(Class<? extends Throwable> type) {
        for (Entry<Class, Continuation> e : handlers.entrySet()) {
            if (e.getKey().isAssignableFrom(type)) return e.getValue();
        }

        return withFinally(super.getExceptionHandler(type));
    }

    /**
     * If the finally block exists, return a {@link Continuation} that evaluates the finally block then
     * proceed to the given continuation.
     */
    Continuation withFinally(Continuation k) {
        if (finally_ == null) return k;
        else return new Finally(k);
    }

    @Override
    public Continuation getReturnAddress() {
        return withFinally(super.getReturnAddress());
    }

    @Override
    public Continuation getBreakAddress(String label) {
        return withFinally(super.getBreakAddress(label));
    }

    @Override
    public Continuation getContinueAddress(String label) {
        return withFinally(super.getContinueAddress(label));
    }

    /**
     * Executes the finally clause, then go back to where it's supposed to be.
     */
    private class Finally implements Continuation {
        private final Continuation k;

        public Finally(Continuation k) {
            this.k = k;
        }

        public Next receive(final Object v) {
            // finally block should be evaluated with 'parent', not 'TryBlockEnv.this' because
            // exceptions thrown in here will not get caught by handlers we have.

            // similarly, k should receive v, not the result of the evaluation of the finally block
            return new Next(finally_, parent, new ValueBoundContinuation(k, v));
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
