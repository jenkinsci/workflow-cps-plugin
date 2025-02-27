/*
 * The MIT License
 *
 * Copyright 2018 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.impl.CallSiteBlock;
import com.cloudbees.groovy.cps.sandbox.DefaultInvoker;
import com.cloudbees.groovy.cps.sandbox.Invoker;
import com.cloudbees.groovy.cps.sandbox.SandboxInvoker;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.Closure;
import groovy.lang.GroovyClassLoader;
import hudson.ExtensionList;
import java.util.Set;
import java.util.function.Supplier;
import jenkins.util.SystemProperties;
import org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper;

/**
 * Captures CPS-transformed events.
 */
final class LoggingInvoker implements Invoker {

    public static Invoker create(boolean sandbox) {
        Invoker delegate = sandbox ? new SandboxInvoker() : new DefaultInvoker();
        return  SystemProperties.getBoolean(LoggingInvoker.class.getName() + ".enabled", true) ? new LoggingInvoker(delegate) : delegate;
    }

    private final Invoker delegate;

    private LoggingInvoker(Invoker delegate) {
        this.delegate = delegate;
    }

    private void record(String call) {
        CpsThreadGroup g = CpsThreadGroup.current();
        if (g == null) {
            assert false : "should never happen";
            return;
        }
        CpsFlowExecution execution = g.getExecution();
        if (execution == null) {
            assert false : "should never happen";
            return;
        }
        execution.recordInternalCall(call);
    }

    private static final Set<Class<?>> IGNORED = Set.of(
        Safepoint.class,
        CpsClosure2.class,
        EnvActionImpl.class,
        RunWrapper.class);

    private static boolean isInternal(Class<?> clazz) {
        if (IGNORED.contains(clazz)) {
            return false;
        }
        if (ExtensionList.lookup(IgnoredInternalClasses.class).stream().anyMatch(iic -> iic.ignore(clazz))) {
            return false;
        }
        if (clazz.getClassLoader() instanceof GroovyClassLoader) { // similar to GroovyClassLoaderWhitelist
            return false;
        }
        // TODO more precise would be the logic in jenkins.security.ClassFilterImpl.isLocationWhitelisted
        // (simply checking whether the class loader can “see”, say, jenkins/model/Jenkins.class
        // would falsely mark third-party libs bundled in Jenkins plugins)
        String name = clazz.getName();
        if (name.startsWith("com.cloudbees.groovy.cps.") || name.startsWith("org.jenkinsci.plugins.workflow.cps.persistence.IteratorHack$")) {
            // Likely synthetic call, as to CpsDefaultGroovyMethods.
            return false;
        }
        // acc. to `find …/jenkinsci/*/src/main/java -type f -exec egrep -h '^package ' {} \; | sort | uniq` this is decent
        return name.contains("jenkins") || name.contains("hudson") || name.contains("cloudbees");
    }

    private void maybeRecord(Class<?> clazz, Supplier<String> message) {
        if (isInternal(clazz)) {
            record(message.get());
        }
    }

    private static @NonNull Class<?> classOf(@CheckForNull Object receiver) {
        if (receiver == null) {
            return Void.class;
        } else if (receiver instanceof Class) {
            return (Class<?>) receiver;
        } else {
            return receiver.getClass();
        }
    }

    @Override public Object methodCall(Object receiver, String method, Object[] args) throws Throwable {
        Class<?> clazz = classOf(receiver);
        maybeRecord(clazz, () -> clazz.getName() + "." + method);
        return delegate.methodCall(receiver, method, args);
    }

    @Override public Object constructorCall(Class lhs, Object[] args) throws Throwable {
        maybeRecord(lhs, () -> lhs.getName() + ".<init>");
        return delegate.constructorCall(lhs, args);
    }

    @Override public Object superCall(Class senderType, Object receiver, String method, Object[] args) throws Throwable {
        maybeRecord(senderType, () -> senderType.getName() + "." + method);
        return delegate.superCall(senderType, receiver, method, args);
    }

    @Override public Object getProperty(Object lhs, String name) throws Throwable {
        Class<?> clazz = classOf(lhs);
        maybeRecord(clazz, () -> clazz.getName() + "." + name);
        return delegate.getProperty(lhs, name);
    }

    @Override public void setProperty(Object lhs, String name, Object value) throws Throwable {
        Class<?> clazz = classOf(lhs);
        maybeRecord(clazz, () -> clazz.getName() + "." + name);
        delegate.setProperty(lhs, name, value);
        if (value != null) {
            var owner = findOwner(lhs);
            if (owner instanceof CpsScript) {
                var g = CpsThreadGroup.current();
                if (g != null) {
                    var e = g.getExecution();
                    if (e != null) {
                        e.getOwner().getListener().getLogger().println(Messages.LoggingInvoker_field_set(findOwner(lhs).getClass().getName(), name, value.getClass().getName()));
                        assert false : "TODO look for false positives";
                    }
                }
            }
        }
    }

    private Object findOwner(Object o) {
        return o instanceof Closure<?> c ? findOwner(c.getOwner()) : o;
    }

    @Override public Object getAttribute(Object lhs, String name) throws Throwable {
        Class<?> clazz = classOf(lhs);
        maybeRecord(clazz, () -> clazz.getName() + "." + name);
        return delegate.getAttribute(lhs, name);
    }

    @Override public void setAttribute(Object lhs, String name, Object value) throws Throwable {
        Class<?> clazz = classOf(lhs);
        maybeRecord(clazz, () -> clazz.getName() + "." + name);
        delegate.setAttribute(lhs, name, value);
    }

    @Override public Object getArray(Object lhs, Object index) throws Throwable {
        return delegate.getArray(lhs, index);
    }

    @Override public void setArray(Object lhs, Object index, Object value) throws Throwable {
        delegate.setArray(lhs, index, value);
    }

    @Override public Object methodPointer(Object lhs, String name) {
        Class<?> clazz = classOf(lhs);
        maybeRecord(clazz, () -> clazz.getName() + "." + name);
        return delegate.methodPointer(lhs, name);
    }

    @Override public Object cast(Object value, Class<?> type, boolean ignoreAutoboxing, boolean coerce, boolean strict) throws Throwable {
        // Nothing obvious to record.
        return delegate.cast(value, type, ignoreAutoboxing, coerce, strict);
    }

    @Override public Invoker contextualize(CallSiteBlock tags) {
        Invoker contextualized = delegate.contextualize(tags);
        return contextualized instanceof LoggingInvoker ? contextualized : new LoggingInvoker(contextualized);
    }

}
