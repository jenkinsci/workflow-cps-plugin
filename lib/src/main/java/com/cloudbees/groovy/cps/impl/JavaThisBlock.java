package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import groovy.lang.Closure;

/**
 * Resolves the current {@link Env#closureOwner()} value.
 *
 * <p>
 * In Groovy, the {@code this} keyword and closure interacts and therefore
 * behaves differently from Java {@code this}. Specifically,
 * Groovy tries to maintain an illusion that closure is a part of the native language
 * feature and tries to hide the fact that it is implemented by another class.
 *
 * <p>
 * So when executing inside a closure, like the following, {@code this} refers to
 * {@code Foo} instance, not the instance of the anonymous closure class, which is the actual
 * {@code this} as far as Java bytecode is concerned at the runtime.
 *
 * <pre>{@code
 * class Foo {
 *   def foo() {
 *     def closure = { ->
 *       this.x = 1;
 *     }
 *     closure();
 *   }
 * }
 * }</pre>
 *
 * <p>
 * So let's refer to the Java version of {@code this} as {code javaThis}, and let's refer
 * to the Groovy version of {@code this} as {@code groovyThis}.
 *
 * <p>
 * To make this more complicated, sometimes Groovy does need to refer to
 * {@code javaThis}, to be able to refer to the closure itself. This happens
 * when you call a method or access a property from inside the closure. In the following
 * code, where a closure makes an assignment to a "dynamic variable" {@code x}, the
 * actual translation of the code fragment is {@code javaThis.x=1} (which would call
 * {@link Closure#setProperty(String, Object)}), not {@code groovyThis.x=1} (which would call
 * {@code Foo#setProperty()}).
 *
 * <pre>{@code
 * class Foo {
 *   def foo() {
 *     def closure = { ->
 *       x = 1;
 *     }
 *     ...
 *   }
 * }
 * }</pre>
 *
 * <p>
 * Groovy defines no explicit syntax for {@code javaThis}. It can be only used implicitly.
 *
 * @author Kohsuke Kawaguchi
 */
public class JavaThisBlock implements Block {
    public Next eval(Env e, Continuation k) {
        return k.receive(e.closureOwner());
    }

    private static final long serialVersionUID = 1L;
}
