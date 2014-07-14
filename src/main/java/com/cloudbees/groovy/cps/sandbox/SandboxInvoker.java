package com.cloudbees.groovy.cps.sandbox;

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

}
