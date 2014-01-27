package com.cloudbees.groovy.cps;

/**
 * For variable lookup. This is local variables.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Env {
    /**
     * Resolves functions visible at the current scope.
     */
    Function resolveFunction(String name);

    Object get(String name);
    void set(String name, Object value);

    /**
     * Creates a new block scope, which doesn't hide current variables, but newly declared variables
     * will be local to the new environment.
     */
    Env newBlockScope();
    // TODO: How do we correctly assign local variables to its scope?
}
