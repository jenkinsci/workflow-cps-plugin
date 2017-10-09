package com.cloudbees.groovy.cps;

/**
 * @author Sam Van Oort
 */
public interface DepthTrackingEnv extends Env {

    /** Limit on how deeply environments can recurse */
    int MAX_LEGAL_DEPTH = 3000;

    public int getDepth();
}
