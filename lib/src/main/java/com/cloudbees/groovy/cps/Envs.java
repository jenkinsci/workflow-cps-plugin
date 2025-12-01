package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.FunctionCallEnv;
import com.cloudbees.groovy.cps.sandbox.Invoker;

/**
 * Utility factory methods for {@link Env}.
 *
 * @author Kohsuke Kawaguchi
 */
public class Envs {
    /**
     * The most plain vanilla environment suitable for outer-most use.
     *
     * Analogous to how {@link Runnable#run()} gets invoked "out of nowhere" when a new {@link Thread} starts in
     * regular Java program.
     */
    public static Env empty() {
        return new FunctionCallEnv(null, Continuation.HALT, null, null, 0);
    }

    /**
     * Works like {@link #empty()} except it allows a custom {@link Invoker}.
     */
    public static Env empty(Invoker inv) {
        FunctionCallEnv e = new FunctionCallEnv(null, Continuation.HALT, null, null, 0);
        e.setInvoker(inv);
        return e;
    }
}
