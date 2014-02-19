package com.cloudbees.groovy.cps;

import groovy.lang.Binding;
import groovy.lang.Script;

import java.io.Serializable;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class SerializableScript extends Script implements Serializable {
    public SerializableScript() {
    }

    public SerializableScript(Binding binding) {
        super(binding);
    }

    private static final long serialVersionUID = 1L;
}
