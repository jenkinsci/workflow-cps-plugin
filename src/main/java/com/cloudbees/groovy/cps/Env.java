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


}
