package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.GroovyClassLoader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.EnumeratingWhitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.ProxyWhitelist;

/**
 * Allow any calls into the scripted compiled in the sandbox.
 *
 * IOW, allow the user to call his own methods.
 */
class GroovyClassLoaderWhitelist extends Whitelist {

    private final Collection<ClassLoader> scriptLoaders;

    /**
     * {@link ProxyWhitelist} has an optimization which bypasses {@code permits*} calls
     * when it detects {@link EnumeratingWhitelist} delegates,
     * so we must do delegation manually.
     * For the same reason, doing this check inside {@link CpsWhitelist} is tricky.
     */
    private final Whitelist delegate;

    GroovyClassLoaderWhitelist(Whitelist delegate, GroovyClassLoader... scriptLoaders) {
        this.scriptLoaders = Arrays.<ClassLoader>asList(scriptLoaders);
        this.delegate = delegate;
    }

    private boolean permits(Class<?> declaringClass) {
        ClassLoader cl = declaringClass.getClassLoader();
        if (cl instanceof GroovyClassLoader.InnerLoader) {
            return scriptLoaders.contains(cl.getParent());
        }
        return scriptLoaders.contains(cl);
    }

    @Override public boolean permitsMethod(Method method, Object receiver, Object[] args) {
        return permits(method.getDeclaringClass()) || delegate.permitsMethod(method, receiver, args);
    }

    @Override public boolean permitsConstructor(Constructor<?> constructor, Object[] args) {
        return permits(constructor.getDeclaringClass()) || delegate.permitsConstructor(constructor, args);
    }

    @Override public boolean permitsStaticMethod(Method method, Object[] args) {
        return permits(method.getDeclaringClass()) || delegate.permitsStaticMethod(method, args);
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

    @Override public String toString() {
        return super.toString() + "[" + delegate + "]";
    }

}
