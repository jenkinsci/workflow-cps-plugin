package com.cloudbees.groovy.cps.sandbox;

import com.cloudbees.groovy.cps.impl.CallSiteBlock;
import org.codehaus.groovy.syntax.Types;
import org.kohsuke.groovy.sandbox.GroovyInterceptor;
import org.kohsuke.groovy.sandbox.impl.Checker;
import org.kohsuke.groovy.sandbox.impl.SandboxedMethodClosure;

/**
 * {@link Invoker} that goes through the groovy-sandbox {@link GroovyInterceptor},
 * so that interactions with Groovy objects can be inspected.
 *
 * @author Kohsuke Kawaguchi
 */
public class SandboxInvoker implements Invoker {
    public Object methodCall(Object receiver, String method, Object[] args) throws Throwable {
        return Checker.checkedCall(receiver, false, false, method, args);
    }

    public Object constructorCall(Class lhs, Object[] args) throws Throwable {
        return Checker.checkedConstructor(lhs, args);
    }

    public Object superCall(Class senderType, Object receiver, String method, Object[] args) throws Throwable {
        return Checker.checkedSuperCall(senderType, receiver, method, args);
    }

    public Object getProperty(Object lhs, String name) throws Throwable {
        return Checker.checkedGetProperty(lhs, false, false, name);
    }

    public void setProperty(Object lhs, String name, Object value) throws Throwable {
        Checker.checkedSetProperty(lhs, name, false, false, Types.ASSIGN, value);
    }

    public Object getAttribute(Object lhs, String name) throws Throwable {
        return Checker.checkedGetAttribute(lhs, false, false, name);
    }

    public void setAttribute(Object lhs, String name, Object value) throws Throwable {
        Checker.checkedSetAttribute(lhs, name, false, false, Types.ASSIGN, value);
    }

    public Object getArray(Object lhs, Object index) throws Throwable {
        return Checker.checkedGetArray(lhs, index);
    }

    public void setArray(Object lhs, Object index, Object value) throws Throwable {
        Checker.checkedSetArray(lhs, index, Types.ASSIGN, value);
    }

    public Object methodPointer(Object lhs, String name) {
        return new SandboxedMethodClosure(lhs, name);
    }

    @Override
    public Object cast(Object value, Class<?> type, boolean ignoreAutoboxing, boolean coerce, boolean strict)
            throws Throwable {
        return Checker.checkedCast(type, value, ignoreAutoboxing, coerce, strict);
    }

    public Invoker contextualize(CallSiteBlock tags) {
        if (tags.getTags().contains(Untrusted.INSTANCE)) return this;
        if (tags.getTags().contains(Trusted.INSTANCE)) return DefaultInvoker.INSTANCE;

        // for compatibility reasons, if the call site doesn't have any tag, we'll assume it's untrusted.
        // this is because we used to not put any tags
        return this;
    }

    private static final long serialVersionUID = 1L;
}
