package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.Conclusion;

import java.io.Serializable;

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
public interface Continuation extends Serializable {
    // this method cannot evaluate any expression on its own
    Next receive(Object o);

    /**
     * Indicates the end of a program.
     */
    final static Continuation HALT = new Halt();

    /**
     * Singleton implementation that maintains the singleton-ness across serialization
     */
    final class Halt implements Continuation {
        private Halt() {
        }

        public Next receive(Object o) {
            return Next.yield(new Conclusion(o,null), null);
        }

        public Object readResolve() {
            return HALT;
        }
    }
}
