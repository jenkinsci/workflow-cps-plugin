package com.cloudbees.groovy.cps.green;

import java.io.Serializable;

/**
 * Stack of monitors that a thread currently owns.
 *
 * @author Kohsuke Kawaguchi
 * @see GreenThreadState#monitor
 */
final class Monitor implements Serializable {
    /**
     * Link to form a stack of monitor.
     */
    final Monitor next;
    /**
     * Object that is locked
     */
    final Object o;

    Monitor(Monitor next, Object o) {
        this.next = next;
        this.o = o;
    }
}
