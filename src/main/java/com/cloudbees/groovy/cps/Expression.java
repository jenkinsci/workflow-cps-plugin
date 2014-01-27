package com.cloudbees.groovy.cps;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Expression {
    Next eval(Env e, Continuation k, Object... args);

    /**
     * A function that does nothing.
     */
    final static Expression NOOP = new Expression() {
        public Next eval(Env e, Continuation k, Object... args) {
            return k.receive(e,null);
        }
    };
}
