package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Next;

/**
 * {@link Continuation} that receives a preset value.
 *
 * @author Kohsuke Kawaguchi
 */
final class ValueBoundContinuation implements Continuation {
    private final Continuation k;
    private final Object v;

    ValueBoundContinuation(Continuation k, Object v) {
        this.k = k;
        this.v = v;
    }

    public Next receive(Object unused) {
        return k.receive(v);
    }

    private static final long serialVersionUID = 1L;
}
