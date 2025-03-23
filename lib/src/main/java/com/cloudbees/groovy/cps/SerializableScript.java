package com.cloudbees.groovy.cps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import groovy.lang.Binding;
import groovy.lang.Script;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class SerializableScript extends Script implements Serializable {
    public SerializableScript() {
    }

    public SerializableScript(Binding binding) {
        super(binding);
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        // binding is defined in non-serializable Script class,
        // so we need to persist that here
        oos.writeObject(getBinding().getVariables());
    }

    @SuppressFBWarnings(value = "MC_OVERRIDABLE_METHOD_CALL_IN_READ_OBJECT", justification = "TODO needs triage")
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        Map m = (Map)ois.readObject();
        getBinding().getVariables().putAll(m);
    }

    private static final long serialVersionUID = 1L;
}
