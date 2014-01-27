package com.cloudbees.groovy.cps;

import static com.cloudbees.groovy.cps.Expression.*;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Continuation {
    // this method cannot evaluate any expression on its own
    Next receive(Object o);

    /**
     * Indicates the end of a program.
     */
    final static Continuation HALT = new Continuation() {
        public Next receive(Object o) {
            Next next = new Next(NOOP, null, HALT);
            next.yield = o;
            return next;
        }
    };
}
