package com.cloudbees.groovy.cps;

/**
 * Represents a variable that's assignable, which is produced
 * by evaluating {@link LValueBlock}, such as "x[y]"
 *
 *
 * @author Kohsuke Kawaguchi
 */
public interface LValue {
    /**
     * Computes the value, and passes it to the given continuation when done.
     */
    Next get(Continuation k);

    /**
     * Sets the given value to this variable, and passes {@code null} to the given continuation when done.
     */
    Next set(Object v, Continuation k);
}
