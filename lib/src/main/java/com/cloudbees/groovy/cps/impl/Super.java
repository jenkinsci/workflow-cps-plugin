package com.cloudbees.groovy.cps.impl;

import java.io.Serializable;

/**
 * Instance of this is used as LHS of a method call
 * to designate {@code super.foo(...)} call.
 *
 * @author Kohsuke Kawaguchi
 */
final class Super implements Serializable {
    /**
     * Type in which the code {@code super.foo(...)} appears.
     */
    final Class senderType;
    /**
     * 'this' object whose method is invoked.
     */
    final Object receiver;

    Super(Class senderType, Object receiver) {
        this.senderType = senderType;
        this.receiver = receiver;
    }

    private static final long serialVersionUID = 1L;
}
