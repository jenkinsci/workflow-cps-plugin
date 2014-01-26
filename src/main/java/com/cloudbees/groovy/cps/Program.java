package com.cloudbees.groovy.cps;

/**
 * @author Kohsuke Kawaguchi
 */
public interface Program {
    ProgramState execute(Continuation k);
}
