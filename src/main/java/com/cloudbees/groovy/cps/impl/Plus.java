package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Expression;
import com.cloudbees.groovy.cps.Next;

/**
 * @author Kohsuke Kawaguchi
 */
public class Plus implements Expression {
    public Next eval(Env e, Continuation k, Object... args) {
        // TODO: this bogus plus operator is just a test
        return k.receive(e, args[0].toString()+args[1].toString());
    }
}
