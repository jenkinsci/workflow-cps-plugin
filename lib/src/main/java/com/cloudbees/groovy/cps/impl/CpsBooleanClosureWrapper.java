package com.cloudbees.groovy.cps.impl;

import groovy.lang.Closure;
import java.io.Serializable;
import java.util.Map;
import org.codehaus.groovy.runtime.callsite.BooleanClosureWrapper;
import org.codehaus.groovy.runtime.callsite.BooleanReturningMethodInvoker;

/**
 * A serializable equivalent of {@link org.codehaus.groovy.runtime.callsite.BooleanClosureWrapper}, where the
 * {@link BooleanReturningMethodInvoker} is instantiated when {@link #call(Object...)} is called to avoid serialization
 * issues with that as well.
 */
public class CpsBooleanClosureWrapper implements Serializable {
    private final Closure wrapped;

    public CpsBooleanClosureWrapper(Closure wrapped) {
        this.wrapped = wrapped;
    }

    /**
     * normal closure call
     */
    public boolean call(Object... args) {
        return new BooleanClosureWrapper(wrapped).call(args);
    }

    /**
     * Bridge for a call based on a map entry. If the call is done on a {@link Closure}
     * taking one argument, then we give in the {@link Map.Entry}, otherwise we will
     * give in the key and value.
     */
    public <K, V> boolean callForMap(Map.Entry<K, V> entry) {
        return new BooleanClosureWrapper(wrapped).callForMap(entry);
    }
}
