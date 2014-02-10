package com.cloudbees.groovy.cps;

/**
 * For variable lookup. This is local variables.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Env {
    void declareVariable(Class type, String name);

    Object getLocalVariable(String name);
    void setLocalVariable(String name, Object value);

    /**
     * Where should the return statement return to?
     */
    Continuation getReturnAddress();

    /**
     * Finds the exception handler that catches a {@link Throwable} instance of this type.
     *
     * @return
     *      never null. Even if there's no user-specified exception handler, the default 'unhandled exception handler'
     *      must be returned.
     */
    Continuation getExceptionHandler(Class<? extends Throwable> type);
}
