package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.Conclusion;

import java.io.Serializable;

/**
 * Variant of {@link Continuation} that can either receive a normal value
 * or receive an exception to be thrown within.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Resumable extends Serializable {
    Next receive(Conclusion o);
}
