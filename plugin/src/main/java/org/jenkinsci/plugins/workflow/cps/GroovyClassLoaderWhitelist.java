package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.GroovyClassLoader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
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

    @Override
    public boolean permitsMethod(Method method, Object receiver, Object[] args) {
        return permits(method.getDeclaringClass()) && !isIllegalSyntheticMethod(method)
                || delegate.permitsMethod(method, receiver, args);
    }

    @Override
    public boolean permitsConstructor(Constructor<?> constructor, Object[] args) {
        return permits(constructor.getDeclaringClass()) && !isIllegalSyntheticConstructor(constructor)
                || delegate.permitsConstructor(constructor, args);
    }

    @Override
    public boolean permitsStaticMethod(Method method, Object[] args) {
        return permits(method.getDeclaringClass()) && !isIllegalSyntheticMethod(method)
                || delegate.permitsStaticMethod(method, args);
    }

    @Override
    public boolean permitsFieldGet(Field field, Object receiver) {
        return permits(field.getDeclaringClass()) && !isIllegalSyntheticField(field)
                || delegate.permitsFieldGet(field, receiver);
    }

    @Override
    public boolean permitsFieldSet(Field field, Object receiver, Object value) {
        return permits(field.getDeclaringClass()) && !isIllegalSyntheticField(field)
                || delegate.permitsFieldSet(field, receiver, value);
    }

    @Override
    public boolean permitsStaticFieldGet(Field field) {
        return permits(field.getDeclaringClass()) && !isIllegalSyntheticField(field)
                || delegate.permitsStaticFieldGet(field);
    }

    @Override
    public boolean permitsStaticFieldSet(Field field, Object value) {
        return permits(field.getDeclaringClass()) && !isIllegalSyntheticField(field)
                || delegate.permitsStaticFieldSet(field, value);
    }

    @Override
    public String toString() {
        return super.toString() + "[" + delegate + "]";
    }

    /**
     * Checks whether a given field was created by the Groovy compiler and should be inaccessible even if it is
     * declared by a class defined by one of the specified class loaders.
     */
    private static boolean isIllegalSyntheticField(Field field) {
        if (!field.isSynthetic()) {
            return false;
        }
        Class<?> declaringClass = field.getDeclaringClass();
        Class<?> enclosingClass = declaringClass.getEnclosingClass();
        if (field.getType() == enclosingClass && field.getName().startsWith("this$")) {
            // Synthetic field added to inner classes to reference the outer class.
            return false;
        } else if (declaringClass.isEnum()
                && Modifier.isStatic(field.getModifiers())
                && field.getName().equals("$VALUES")) {
            // Synthetic field added to enum classes to hold the enum constants.
            return false;
        }
        return true;
    }

    /**
     * Checks whether a given method was created by the Groovy compiler and should be inaccessible even if it is
     * declared by a class defined by one of the specified class loaders.
     */
    private static boolean isIllegalSyntheticMethod(Method method) {
        if (!method.isSynthetic()) {
            return false;
        } else if (Modifier.isStatic(method.getModifiers())
                && method.getDeclaringClass().isEnum()
                && method.getName().equals("$INIT")) {
            // Synthetic method added to enum classes used to initialize the enum constants.
            return false;
        }
        return true;
    }

    /**
     * Checks whether a given constructor was created by the Groovy compiler (or groovy-sandbox) and
     * should be inaccessible even if it is declared by a class defined by the specified class loader.
     */
    private static boolean isIllegalSyntheticConstructor(Constructor constructor) {
        if (!constructor.isSynthetic()) {
            return false;
        }
        Class<?> declaringClass = constructor.getDeclaringClass();
        Class<?> enclosingClass = declaringClass.getEnclosingClass();
        if (enclosingClass != null
                && constructor.getParameters().length > 0
                && constructor.getParameterTypes()[0] == enclosingClass) {
            // Synthetic constructor added by Groovy to anonymous classes.
            return false;
        }
        return true;
    }
}
