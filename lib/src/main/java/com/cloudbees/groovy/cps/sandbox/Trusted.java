package com.cloudbees.groovy.cps.sandbox;

/**
 * {@link CallSiteTag} to instruct {@link SandboxInvoker} that this code is trusted and will bypass sandbox check.
 *
 * @author Kohsuke Kawaguchi
 * @see Untrusted
 */
public final class Trusted implements CallSiteTag {
    // singleton
    private Trusted() {}

    private Object readResolve() {
        return INSTANCE;
    }

    private static final long serialVersionUID = 1L;

    public static final Trusted INSTANCE = new Trusted();
}
