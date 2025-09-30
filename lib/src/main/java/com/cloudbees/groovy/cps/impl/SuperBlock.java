package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * @author Kohsuke Kawaguchi
 */
public class SuperBlock implements Block {
    private final Class senderType;

    public SuperBlock(Class senderType) {
        this.senderType = senderType;
    }

    public Next eval(Env e, Continuation k) {
        return k.receive(new Super(senderType, e.closureOwner()));
    }

    private static final long serialVersionUID = 1L;
}
