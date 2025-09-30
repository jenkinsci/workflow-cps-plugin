package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.MethodLocation;
import java.io.Serializable;

/**
 * Represents a specific location of the source file.
 *
 * @author Kohsuke Kawaguchi
 */
public final class SourceLocation implements Serializable {
    private final MethodLocation method;
    private final int lineNumber;

    public SourceLocation(MethodLocation method, int lineNumber) {
        this.method = method;
        this.lineNumber = lineNumber;
    }

    public StackTraceElement toStackTrace() {
        return method.toStackTrace(lineNumber);
    }

    @Override
    public String toString() {
        return toStackTrace().toString();
    }

    private static final long serialVersionUID = 1L;

    public static final SourceLocation UNKNOWN = new SourceLocation(MethodLocation.UNKNOWN, -1);
}
