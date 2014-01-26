package com.cloudbees.groovy.cps;

/**
 * @author Kohsuke Kawaguchi
 */
public class ProgramState {
    Program p;
    Continuation k;

    ProgramState step() {
        return p.execute(k);
    }

    Object
}
