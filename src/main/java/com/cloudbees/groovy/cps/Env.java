package com.cloudbees.groovy.cps;

/**
 * For variable lookup. This is local variables.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Env {
    // TODO: How do we correctly assign local variables to its scope?

    void declareVariable(String name);

    Object getLocalVariable(String name);
    void setLocalVariable(String name, Object value);

    /**
     * Where should the return statement return to?
     */
    Continuation getReturnAddress();

    /**
     * Finds the exception handler that catches a {@link Throwable} instance of this type.
     */
    Continuation getExceptionHandler(Class<? extends Throwable> type);
}
