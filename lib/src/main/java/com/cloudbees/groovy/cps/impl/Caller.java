package com.cloudbees.groovy.cps.impl;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;

/**
 * As a crude way to distinguish asynchronous caller vs synchronous caller,
 * remember the method call about to happen so that the callee can check
 * if it is invoked by asynchronous caller or not.
 *
 * @author Kohsuke Kawaguchi
 */
public class Caller {
    /**
     * Caller information needs to be recorded per thread.
     */
    private static final ThreadLocal<Info> store = ThreadLocal.withInitial(Info::new);

    /**
     * Checks if the method is called from asynchronous CPS transformed code.
     *
     * <p>
     * This method must be the first call in the function body.
     */
    public static boolean isAsynchronous(Object receiver, String method, Object... args) {
        Info i = store.get();
        return i.receiver != null
                && receiver == i.receiver.get()
                && method.equals(i.method)
                && arrayShallowEquals(i.args, args);
    }

    private static boolean arrayShallowEquals(Reference<Object>[] a, Object[] b) {
        if (a.length != b.length) return false;
        for (int i = 0; i < a.length; i++) if (a[i].get() != b[i]) return false;
        return true;
    }

    public static boolean isAsynchronous(Object receiver, String method) {
        Info i = store.get();
        return i.receiver != null && receiver == i.receiver.get() && method.equals(i.method) && i.args.length == 0;
    }

    public static boolean isAsynchronous(Object receiver, String method, Object arg1) {
        Info i = store.get();
        return i.receiver != null
                && receiver == i.receiver.get()
                && method.equals(i.method)
                && i.args.length == 1
                && i.args[0].get() == arg1;
    }

    public static boolean isAsynchronous(Object receiver, String method, Object arg1, Object arg2) {
        Info i = store.get();
        return i.receiver != null
                && receiver == i.receiver.get()
                && method.equals(i.method)
                && i.args.length == 2
                && i.args[0].get() == arg1
                && i.args[1].get() == arg2;
    }

    static class Info {
        private Reference<Object> receiver;
        private String method;
        private Reference<Object>[] args;
    }

    @SuppressWarnings({"unchecked", "rawtypes"}) // generic array creation
    static void record(Object receiver, String method, Object[] args) {
        Info c = store.get();
        c.receiver = new WeakReference<>(receiver);
        c.method = method;
        c.args = new WeakReference[args.length];
        for (int i = 0; i < args.length; i++) {
            c.args[i] = new WeakReference<>(args[i]);
        }
    }
}
