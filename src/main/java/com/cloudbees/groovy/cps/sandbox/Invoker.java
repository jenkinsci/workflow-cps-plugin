package com.cloudbees.groovy.cps.sandbox;

import com.cloudbees.groovy.cps.Env;

import java.io.Serializable;

/**
 * Abstracts away interactions with Groovy objects, for example to provide an opportunity to intercept
 * calls.
 *
 * <p>
 * During the execution of CPS code, {@link Invoker} is available from {@link Env#getInvoker()}.
 *
 * @author Kohsuke Kawaguchi
 * @see Env#getInvoker()
 */
public interface Invoker extends Serializable {
    /**
     * Default instance to be used.
     */
    Invoker INSTANCE = new DefaultInvoker();

    Object methodCall(Object receiver, String method, Object[] args) throws Throwable;

    Object constructorCall(Class lhs, Object[] args) throws Throwable;

    Object getProperty(Object lhs, String name) throws Throwable;

    void setProperty(Object lhs, String name, Object value) throws Throwable;

    Object getAttribute(Object lhs, String name) throws Throwable;

    void setAttribute(Object lhs, String name, Object value) throws Throwable;

    Object getArray(Object lhs, Object index) throws Throwable;

    void setArray(Object lhs, Object index, Object value) throws Throwable;
}
