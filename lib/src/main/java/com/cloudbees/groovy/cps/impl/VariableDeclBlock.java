package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
public class VariableDeclBlock implements Block {
    private final Class type;
    private final String name;

    public VariableDeclBlock(Class type, String name) {
        this.type = type;
        this.name = name;
    }

    public Next eval(final Env e, final Continuation k) {
        e.declareVariable(type, name);
        e.setLocalVariable(name, defaultPrimitiveValue.get(type));
        return k.receive(null);
    }

    private static final long serialVersionUID = 1L;

    private static final Map<Class, Object> defaultPrimitiveValue = new HashMap<>();

    static {
        defaultPrimitiveValue.put(boolean.class, false);
        defaultPrimitiveValue.put(int.class, 0);
        defaultPrimitiveValue.put(long.class, 0L);
        defaultPrimitiveValue.put(float.class, 0.0f);
        defaultPrimitiveValue.put(double.class, 0.0d);
        // TODO: complete the rest
    }
}
