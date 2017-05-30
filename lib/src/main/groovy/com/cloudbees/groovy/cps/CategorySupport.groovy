package com.cloudbees.groovy.cps

import org.codehaus.groovy.runtime.GroovyCategorySupport

import java.util.concurrent.Callable

/**
 * Invokes {@link GroovyCategorySupport#use(java.lang.Class, groovy.lang.Closure)} with {@link Runnable}.
 *
 * @author Kohsuke Kawaguchi
 */
class CategorySupport {
    public static <T> T use(Class category, Callable<T> r) {
        return GroovyCategorySupport.use(category) {
            return r.call();
        }
    }
}
