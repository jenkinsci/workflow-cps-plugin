package com.cloudbees.groovy.cps;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Continuation {
    void execute(Object r);

    /**
     * Indicates the end of a program.
     */
    final static Continuation HALT = new Continuation() {
        public void execute(Object r) {
            throw new IllegalStateException();
        }
    };
}
