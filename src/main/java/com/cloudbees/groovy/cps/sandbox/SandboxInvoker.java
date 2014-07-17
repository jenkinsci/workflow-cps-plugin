package com.cloudbees.groovy.cps.sandbox;

import org.codehaus.groovy.syntax.Types;
import org.kohsuke.groovy.sandbox.GroovyInterceptor;
import org.kohsuke.groovy.sandbox.impl.Checker;

/**
 * {@link Invoker} that goes through the groovy-sandbox {@link GroovyInterceptor},
 * so that interactions with Groovy objects can be inspected.
 *
 * @author Kohsuke Kawaguchi
 */
public class SandboxInvoker implements Invoker {
    public Object methodCall(Object receiver, String method, Object[] args) throws Throwable {
        return Checker.checkedCall(receiver,false,false,method,args);
    }

    public Object constructorCall(Class lhs, Object[] args) throws Throwable {
        return Checker.checkedConstructor(lhs,args);
    }

    public Object getProperty(Object lhs, String name) throws Throwable {
        return Checker.checkedGetProperty(lhs,false,false,name);
    }

    public void setProperty(Object lhs, String name, Object value) throws Throwable {
        Checker.checkedSetProperty(lhs,name,false,false, Types.ASSIGN,value);
    }

    public Object getAttribute(Object lhs, String name) throws Throwable {
        return Checker.checkedGetAttribute(lhs, false, false, name);
    }

    public void setAttribute(Object lhs, String name, Object value) throws Throwable {
        Checker.checkedSetAttribute(lhs, name, false, false, Types.ASSIGN, value);
    }

    public Object getArray(Object lhs, Object index) throws Throwable {
        return Checker.checkedGetArray(lhs,index);
    }

    public void setArray(Object lhs, Object index, Object value) throws Throwable {
        Checker.checkedSetArray(lhs,index,Types.ASSIGN,value);
    }

    private static final long serialVersionUID = 1L;
}
