package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;

/**
 * Pass in this value in {@link Continuable#suspend(Object)} to create a new green thread.
 *
 * A new thread will be created, and {@link Continuable#suspend(Object)} will return with
 * the {@link GreenThread} object. TODO: but GreenThread object is not useful because it's immutable
 *
 * @author Kohsuke Kawaguchi
 */
public class GreenThreadCreation {
    final Block block;

    public GreenThreadCreation(Block block) {
        this.block = block;
    }
}
