package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Expression;
import com.cloudbees.groovy.cps.Next;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class Constant implements Expression {
    private final Object value;

    public Constant(Object value) {
        this.value = value;
    }

    public Next eval(Env e, Continuation k) {
        return k.receive(value);
    }
}
