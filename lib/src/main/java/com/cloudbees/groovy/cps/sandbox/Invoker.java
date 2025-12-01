package com.cloudbees.groovy.cps.sandbox;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Envs;
import com.cloudbees.groovy.cps.impl.CallSiteBlock;
import groovy.lang.Script;
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
 * @see Continuable#Continuable(Script, Env)
 * @see Envs#empty(Invoker)
 * @see "doc/sandbox.md"
 */
public interface Invoker extends Serializable {
    /**
     * Default instance to be used.
     */
    Invoker INSTANCE = new DefaultInvoker();

    Object methodCall(Object receiver, String method, Object[] args) throws Throwable;

    Object constructorCall(Class lhs, Object[] args) throws Throwable;

    /**
     * Invokespecial equivalent used for "super.foo(...)" kind of method call.
     *
     * @param senderType
     *      The type of the current method. Resolution of 'super' depends on this.
     *      'receiver' is an instance of this type.
     * @param receiver
     *      Instance that gets the method call
     */
    Object superCall(Class senderType, Object receiver, String method, Object[] args) throws Throwable;

    Object getProperty(Object lhs, String name) throws Throwable;

    void setProperty(Object lhs, String name, Object value) throws Throwable;

    Object getAttribute(Object lhs, String name) throws Throwable;

    void setAttribute(Object lhs, String name, Object value) throws Throwable;

    Object getArray(Object lhs, Object index) throws Throwable;

    void setArray(Object lhs, Object index, Object value) throws Throwable;

    Object methodPointer(Object lhs, String name);

    Object cast(Object value, Class<?> type, boolean ignoreAutoboxing, boolean coerce, boolean strict) throws Throwable;

    /**
     * Returns a child {@link Invoker} used to make a call on behalf of the given {@link CallSiteBlock}.
     */
    Invoker contextualize(CallSiteBlock tags);
}
