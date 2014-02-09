package com.cloudbees.groovy.cps;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class Constant implements Block {
    public final Object value;

    public Constant(Object value) {
        this.value = value;
    }

    public Next eval(Env e, Continuation k) {
        return k.receive(value);
    }
}
