package com.cloudbees.groovy.cps.sandbox;

import com.cloudbees.groovy.cps.Env;

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
public interface Invoker {
    Object methodCall(Object receiver, boolean safe, boolean spread, String method, Object[] args) throws Throwable;

    /**
     * Default instance to be used.
     */
    Invoker INSTANCE = new DefaultInvoker();
}
