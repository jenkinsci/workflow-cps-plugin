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
class GreenThreadCreation implements ThreadTask {
    final GreenThread g;
    final Block block;

    public GreenThreadCreation(GreenThread g, Block block) {
        this.g = g;
        this.block = block;
    }

    public Result eval(GreenDispatcher d) {
        d = d.withNewThread(new GreenThreadState(g,block));
        return new Result(d, new Outcome(g,null), false);
    }
}
