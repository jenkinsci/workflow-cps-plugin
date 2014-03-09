package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.impl.Outcome;

/**
 * @author Kohsuke Kawaguchi
 */
interface ThreadTask {
    Result eval(GreenWorld d);
}

class Result<T> {
    /**
     * Next state of the world
     */
    final GreenWorld d;
    /**
     * value to be yielded or returned from suspension.
     */
    final Outcome value;
    /**
     * Should {@link #value} be yielded to the caller of {@link Continuable#run(Object)} (true)
     * or should we immediately return from {@link Continuable#suspend(Object)}? (false)
     */
    final boolean suspend;

    Result(GreenWorld d, Outcome value, boolean suspend) {
        this.d = d;
        this.value = value;
        this.suspend = suspend;
    }
}
