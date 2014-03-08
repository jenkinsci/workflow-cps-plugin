package com.cloudbees.groovy.cps.impl;

import java.lang.reflect.InvocationTargetException;

/**
 * Result of the evaluation.
 *
 * Either represents a value in case of a normal return, or a throwable object in case of abnormal return.
 * Note that both fields can be null, in which case it means a normal return of the value 'null'.
 *
 * @author Kohsuke Kawaguchi
 */
public final class Conclusion {
    private final Object normal;
    private final Throwable abnormal;

    public Conclusion(Object normal, Throwable abnormal) {
        assert normal==null || abnormal==null;
        this.normal = normal;
        this.abnormal = abnormal;
    }

    public Object replay() throws InvocationTargetException {
        if (abnormal!=null)
            throw new InvocationTargetException(abnormal);
        else
            return normal;
    }

    public Object getNormal() {
        return normal;
    }

    public Throwable getAbnormal() {
        return abnormal;
    }
}
