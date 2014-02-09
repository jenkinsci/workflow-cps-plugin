package com.cloudbees.groovy.cps;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Block {
    Next eval(Env e, Continuation k);

    /**
     * A function that does nothing.
     */
    final static Block NOOP = new Block() {
        public Next eval(Env e, Continuation k) {
            return k.receive(null);
        }
    };
}
