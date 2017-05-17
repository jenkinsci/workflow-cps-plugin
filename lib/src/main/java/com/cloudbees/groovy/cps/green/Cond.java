package com.cloudbees.groovy.cps.green;

/**
 * Condition that blocks {@link GreenThreadState} from running.
 *
 * The target object of the monitor is kept in {@link GreenThreadState#wait}
 *
 * @author Kohsuke Kawaguchi
 */
enum Cond {
    /**
     * Trying to acquire a monitor.
     * Equivalent of monitor_enter JVM bytecode.
     */
    MONITOR_ENTER,
    /**
     * Temporarily released a monitor and waiting to be notified.
     * Equivalent of {@link Object#wait()}
     */
    WAIT,
    /**
     * The thread was notified after waiting, and trying to reacquire a monitor.
     *
     * Unlike {@link #MONITOR_ENTER}, when a lock is acquired this will not add a new {@link Monitor}.
     */
    NOTIFIED
}
