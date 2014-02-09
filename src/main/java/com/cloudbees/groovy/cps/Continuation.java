package com.cloudbees.groovy.cps;

import static com.cloudbees.groovy.cps.Block.*;

/**
 * Represents the remaining computation that receives the result of {@link Block}.
 *
 * <p>
 * To maintain backward compatibility with serialized {@link Continuation} objects, it is preferable
 * to avoid anonymous single-method classes that implement {@link Continuation}. See {@link com.cloudbees.groovy.cps.impl.ContinuationGroup}
 * for how to do this.
 *
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
            next.yield(o);
            return next;
        }
    };
}
