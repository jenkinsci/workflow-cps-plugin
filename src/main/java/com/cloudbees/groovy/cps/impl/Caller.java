package com.cloudbees.groovy.cps.impl;

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
    private static final ThreadLocal<Info> store = new ThreadLocal<Info>() {
        @Override
        protected Info initialValue() {
            return new Info();
        }
    };

    /**
     * Checks if the method is called from asynchronous CPS transformed code.
     *
     * <p>
     * This method must be the first call in the function body.
     */
    public static boolean isAsynchronous(Object receiver, String method, Object... args) {
        Info i = store.get();
        return receiver==i.receiver && method.equals(i.method) && arrayShallowEquals(i.args, args);
    }

    private static boolean arrayShallowEquals(Object[] a, Object[] b) {
        if (a.length!=b.length)     return false;
        for (int i=0; i<a.length; i++)
            if (a[i]!=b[i])
                return false;
        return true;
    }

    public static boolean isAsynchronous(Object receiver, String method) {
        Info i = store.get();
        return receiver==i.receiver && method.equals(i.method) && i.args.length==0;
    }

    public static boolean isAsynchronous(Object receiver, String method, Object arg1) {
        Info i = store.get();
        return receiver==i.receiver && method.equals(i.method) && i.args.length==1 && i.args[0]==arg1;
    }

    public static boolean isAsynchronous(Object receiver, String method, Object arg1, Object arg2) {
        Info i = store.get();
        return receiver==i.receiver && method.equals(i.method) && i.args.length==2 && i.args[0]==arg1 && i.args[1]==arg2;
    }

    static class Info {
        private Object receiver;
        private String method;
        private Object[] args;
    }

    static void record(Object receiver, String method, Object[] args) {
        Info c = store.get();
        c.receiver = receiver;
        c.method = method;
        c.args = args;
    }
}
