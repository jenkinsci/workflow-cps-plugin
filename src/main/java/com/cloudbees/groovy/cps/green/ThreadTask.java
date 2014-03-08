package com.cloudbees.groovy.cps.green;

/**
 * @author Kohsuke Kawaguchi
 */
abstract interface ThreadTask<T> {
    T eval(GreenDispatcher d) throws Throwable;
}
