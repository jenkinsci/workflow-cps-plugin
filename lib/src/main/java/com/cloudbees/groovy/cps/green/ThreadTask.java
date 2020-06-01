package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;

/**
 * @author Kohsuke Kawaguchi
 */
interface ThreadTask {
    Result eval(GreenWorld w);
}

class Result<T> {
    /**
     * Next state of the world
     */
    final GreenWorld w;
    /**
     * value to be yielded or returned from suspension.
     */
    final Outcome value;
    /**
     * Should {@link #value} be yielded to the caller of {@link Continuable#run(Object)} (true)
     * or should we immediately return from {@link Continuable#suspend(Object)}? (false)
     */
    final boolean suspend;

    Result(GreenWorld w, Outcome value, boolean suspend) {
        this.w = w;
        this.value = value;
        this.suspend = suspend;
    }
}
