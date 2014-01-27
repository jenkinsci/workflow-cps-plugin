package com.cloudbees.groovy.cps;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Expression {
    Next eval(Env e, Continuation k);

    /**
     * A function that does nothing.
     */
    final static Expression NOOP = new Expression() {
        public Next eval(Env e, Continuation k) {
            return k.receive(null);
        }
    };
}
