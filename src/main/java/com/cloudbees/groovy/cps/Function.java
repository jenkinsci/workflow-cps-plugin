package com.cloudbees.groovy.cps;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Function {
    Next apply(Env e, Continuation k, Object... args);

    /**
     * Indicates the end of a program.
     */
    final static Function HALT = new Function() {
        public Next apply(Env e, Continuation k, Object... args) {
            Next next = new Next(HALT, e, k);
            next.yield = args[0];
            return next;
        }
    };
}
