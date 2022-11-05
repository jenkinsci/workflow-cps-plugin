package com.cloudbees.groovy.cps;

/**
 * @author Sam Van Oort
 */
public interface DepthTrackingEnv extends Env {

    /** Limit on how deeply environments can recurse.
     *  Capped somewhat low to try to limit the ability to run a program that will generate a StackOverflowError when serialized. */
    int MAX_LEGAL_DEPTH = 1024;

    /** Return how deep this environment is within nested closure/function calls. */
    int getDepth();
}
