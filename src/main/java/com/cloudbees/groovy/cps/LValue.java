package com.cloudbees.groovy.cps;

import java.io.Serializable;

/**
 * Represents a variable that's assignable, which is produced
 * by evaluating {@link LValueBlock}, such as "x[y]"
 *
 *
 * @author Kohsuke Kawaguchi
 */
public interface LValue extends Serializable {
    /**
     * Computes the value, and passes it to the given continuation when done.
     *
     * <p>
     * Just like {@link Block#eval(Env, Continuation)}, the actual evaluation is done
     * by the caller by repeatedly {@linkplain Next#step() step executing}
     * the resulting {@link Next} object.
     */
    Next get(Continuation k);

    /**
     * Sets the given value to this variable, and passes {@code null} to the given continuation when done.
     *
     * <p>
     * Just like {@link Block#eval(Env, Continuation)}, the actual evaluation is done
     * by the caller by repeatedly {@linkplain Next#step() step executing}
     * the resulting {@link Next} object.
     */
    Next set(Object v, Continuation k);
}
