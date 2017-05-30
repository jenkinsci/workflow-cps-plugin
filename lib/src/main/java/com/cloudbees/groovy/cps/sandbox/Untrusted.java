package com.cloudbees.groovy.cps.sandbox;

/**
 * {@link CallSiteTag} to instruct {@link SandboxInvoker} that this code is untrusted and should be under sandbox check.
 *
 * @author Kohsuke Kawaguchi
 * @see Trusted
 */
public class Untrusted implements CallSiteTag {
    // singleton
    private Untrusted() {}

    private Object readResolve() {
        return INSTANCE;
    }

    private static final long serialVersionUID = 1L;

    public static final Untrusted INSTANCE = new Untrusted();
}
