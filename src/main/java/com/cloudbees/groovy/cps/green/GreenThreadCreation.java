package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.impl.Outcome;

/**
 * Pass in this value in {@link Continuable#suspend(Object)} to create a new green thread.
 *
 * A new thread will be created, and {@link Continuable#suspend(Object)} will return with
 * the {@link GreenThreadState} object.
 *
 * @author Kohsuke Kawaguchi
 */
class GreenThreadCreation {
    final Block block;

    public GreenThreadCreation(Block block) {
        this.block = block;
    }
}
