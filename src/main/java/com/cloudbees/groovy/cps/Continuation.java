package com.cloudbees.groovy.cps;

import static com.cloudbees.groovy.cps.Expression.*;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Continuation {
    // this method cannot evaluate any expression on its own
    // TODO: does 'env' makes sense here?
    Next receive(Env e, Object o);

    /**
     * Indicates the end of a program.
     */
    final static Continuation HALT = new Continuation() {
        public Next receive(Env e, Object o) {
            Next next = new Next(NOOP, e, HALT);
            next.yield = o;
            return next;
        }
    };
}
