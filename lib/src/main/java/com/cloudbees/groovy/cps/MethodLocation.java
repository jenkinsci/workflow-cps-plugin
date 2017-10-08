package com.cloudbees.groovy.cps;

import java.io.Serializable;
import java.util.Objects;

/**
 * Triplet of source file / declaring class / method name.
 *
 * Separated from {@link com.cloudbees.groovy.cps.impl.SourceLocation} for better reuse.
 *
 * @author Kohsuke Kawaguchi
 * @see com.cloudbees.groovy.cps.impl.SourceLocation
 */
public final class MethodLocation implements Serializable {
    private final String declaringClass;
    private final String methodName;
    private final String fileName;

    public MethodLocation(Class clazz, String methodName) {
        this(clazz.getName(), methodName, clazz.getSimpleName());
    }

    public MethodLocation(String declaringClass, String methodName, String fileName) {
        this.declaringClass = declaringClass;
        this.methodName = methodName;
        this.fileName = fileName;
    }

    public String getDeclaringClass() {
        return declaringClass;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getFileName() {
        return fileName;
    }

    public StackTraceElement toStackTrace(int lineNumber) {
        return new StackTraceElement(declaringClass, methodName, fileName, lineNumber);
    }

    /**
     * Constant in case source location information is unavailable.
     */
    public static final MethodLocation UNKNOWN = new MethodLocation("Unknown", "Unknown", "Unknown");

    private static final long serialVersionUID = 1L;

    @Override
    public int hashCode() {
        return Objects.hash(declaringClass, methodName, fileName);
    }

    @Override
    public boolean equals(Object o){
        if (o == this) {
            return  true;
        } else if (!(o instanceof MethodLocation)) {
            return false;
        } else {
            MethodLocation ob = (MethodLocation)o;
            return this.methodName.equals(ob.methodName)
                    && this.fileName.equals(ob.fileName)
                    && this.declaringClass.equals(ob.declaringClass);
        }
    }
}
