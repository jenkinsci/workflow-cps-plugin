package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Used to bind an instance method to a {@link Continuation} object.
 *
 * <p>
 * To wrap an instance method to a {@link Continuation}, we need three parameters:
 * the class on which the method is defined, the name of the method, and the receiver instance.
 *
 * <p>
 * For a performance reason, the first two parameters are specified via the constructor, and the last
 * parameter is given to the {@link #bind(Object)} method. This allows {@link ContinuationPtr}s to be
 * created as static singletons.
 *
 * @see ContinuationGroup#then(Block, Env, ContinuationPtr)
 * @author Kohsuke Kawaguchi
 */
class ContinuationPtr implements Serializable {
    private transient /*final except serialization*/ Method m;

    ContinuationPtr(Class<?> type, String methodName) {
        resolveMethod(type, methodName);
    }

    private void resolveMethod(Class<?> type, String methodName) {
        try {
            m = type.getMethod(methodName, Object.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Binds the pointer to a continuation method to a specific receiver instance.
     */
    Continuation bind(final Object target) {
        return new ContinuationImpl(target);
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        Class c = (Class) ois.readObject();
        String methodName = ois.readUTF();
        resolveMethod(c, methodName);
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.writeObject(m.getDeclaringClass());
        oos.writeUTF(m.getName());
    }

    private class ContinuationImpl implements Continuation {
        private final Object target;

        public ContinuationImpl(Object target) {
            this.target = target;
        }

        public Next receive(Object o) {
            try {
                return (Next) m.invoke(target, o);
            } catch (IllegalAccessException e) {
                throw (IllegalAccessError) new IllegalAccessError().initCause(e);
            } catch (InvocationTargetException e) {
                Throwable t = e.getTargetException();
                if (t instanceof Error) throw (Error) t;
                if (t instanceof RuntimeException) throw (RuntimeException) t;
                throw new Error(e);
            }
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
