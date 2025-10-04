package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;

/**
 * @author Kohsuke Kawaguchi
 */
public class CaseEnv extends ProxyEnv {
    final String label;
    final Continuation break_;

    public CaseEnv(Env parent, String label, Continuation break_) {
        super(parent);
        this.label = label;
        this.break_ = break_;
    }

    @Override
    public Continuation getBreakAddress(String label) {
        if (labelMatch(label)) return break_;
        else return super.getBreakAddress(label);
    }

    private boolean labelMatch(String given) {
        return given == null || given.equals(label);
    }
}
