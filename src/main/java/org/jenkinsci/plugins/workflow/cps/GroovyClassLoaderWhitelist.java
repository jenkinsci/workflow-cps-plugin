package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.impl.CpsClosure;
import groovy.lang.GroovyClassLoader;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.EnumeratingWhitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.ProxyWhitelist;

/**
 * Allow any calls into the scripted compiled in the sandbox.
 *
 * IOW, allow the user to call his own methods.
 */
class GroovyClassLoaderWhitelist extends Whitelist {

    private final ClassLoader scriptLoader;

    /**
     * {@link ProxyWhitelist} has an optimization which bypasses {@code permits*} calls
     * when it detects {@link EnumeratingWhitelist} delegates,
     * so we must do delegation manually.
     * For the same reason, doing this check inside {@link CpsWhitelist} is tricky.
     */
    private final Whitelist delegate;

    GroovyClassLoaderWhitelist(GroovyClassLoader scriptLoader, Whitelist delegate) {
        this.scriptLoader = scriptLoader;
        this.delegate = delegate;
    }

    private boolean permits(Class<?> declaringClass) {
        ClassLoader cl = declaringClass.getClassLoader();
        if (cl instanceof GroovyClassLoader.InnerLoader) {
            return cl.getParent()==scriptLoader;
        }
        return cl == scriptLoader;
    }

    @Override public boolean permitsMethod(Method method, Object receiver, Object[] args) {
        return checkJenkins26481(args, method) || delegate.permitsMethod(method, receiver, args);
    }

    @Override public boolean permitsConstructor(Constructor<?> constructor, Object[] args) {
        return checkJenkins26481(args, constructor) || delegate.permitsConstructor(constructor, args);
    }

    @Override public boolean permitsStaticMethod(Method method, Object[] args) {
        return checkJenkins26481(args, method) || delegate.permitsStaticMethod(method, args);
    }

    @Override public boolean permitsFieldGet(Field field, Object receiver) {
        return permits(field.getDeclaringClass()) || delegate.permitsFieldGet(field, receiver);
    }

    @Override public boolean permitsFieldSet(Field field, Object receiver, Object value) {
        return permits(field.getDeclaringClass()) || delegate.permitsFieldSet(field, receiver, value);
    }

    @Override public boolean permitsStaticFieldGet(Field field) {
        return permits(field.getDeclaringClass()) || delegate.permitsStaticFieldGet(field);
    }

    @Override public boolean permitsStaticFieldSet(Field field, Object value) {
        return permits(field.getDeclaringClass()) || delegate.permitsStaticFieldSet(field, value);
    }

    /**
     * Blocks accesses to some {@link DefaultGroovyMethods}, or any other binary method taking a closure, until JENKINS-26481 is fixed.
     * Only improves diagnostics for scripts using the sandbox.
     * Note that we do not simply return false for these cases, as that would throw {@link RejectedAccessException} without a meaningful explanation.
     */
    private boolean checkJenkins26481(Object[] args, Executable method) throws UnsupportedOperationException {
        if (permits(method.getDeclaringClass())) { // fine for source-defined methods to take closures
            return true;
        }
        for (Object arg : args) {
            if (arg instanceof CpsClosure) {
                throw new UnsupportedOperationException("Calling " + method + " on a CPS-transformed closure is not yet supported (JENKINS-26481); encapsulate in a @NonCPS method, or use Java-style loops");
            }
        }
        return false;
    }

}
