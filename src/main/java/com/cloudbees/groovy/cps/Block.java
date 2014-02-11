package com.cloudbees.groovy.cps;

import java.io.Serializable;

/**
 * AST Node of Groovy for CPS execution.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Block extends Serializable {
    /**
     * Executes this expression, then pass the result to the given continuation when it's available.
     *
     * <p>
     * To be more precise, this method does not evaluate the expression by itself synchronously.
     * Instead, the evaluation is done by the caller by repeatedly {@linkplain Next#step() step executing}
     * the resulting {@link Next} object.
     */
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
