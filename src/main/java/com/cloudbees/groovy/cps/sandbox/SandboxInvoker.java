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
    public Object methodCall(Object receiver, boolean safe, boolean spread, String method, Object[] args) throws Throwable {
        return Checker.checkedCall(receiver,safe,spread,method,args);
    }

    public Object getProperty(Object lhs, boolean safe, boolean spread, String name) throws Throwable {
        return Checker.checkedGetProperty(lhs,safe,spread,name);
    }

    public void setProperty(Object lhs, String name, boolean safe, boolean spread, Object value) throws Throwable {
        Checker.checkedSetProperty(lhs,name,safe,spread, Types.ASSIGN,value);
    }

    public Object getAttribute(Object lhs, boolean safe, boolean spread, String name) throws Throwable {
        return Checker.checkedGetAttribute(lhs, safe, spread, name);
    }

    public void setAttribute(Object lhs, String name, boolean safe, boolean spread, Object value) throws Throwable {
        Checker.checkedSetAttribute(lhs, name, safe, spread, Types.ASSIGN, value);
    }

    public Object getArray(Object lhs, Object index) throws Throwable {
        return Checker.checkedGetArray(lhs,index);
    }

    public void setArray(Object lhs, Object index, Object value) throws Throwable {
        Checker.checkedSetArray(lhs,index,Types.ASSIGN,value);
    }
}
