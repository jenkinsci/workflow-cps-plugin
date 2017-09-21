package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Env;
import com.google.common.collect.Maps;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * {@link Env} for a new block.
 *
 * @author Kohsuke Kawaguchi
 */
// TODO: should be package local once all the impls move into this class
public class BlockScopeEnv extends ProxyEnv {
    private Map<String,Object> locals;
    private Map<String, Class> types;

    public BlockScopeEnv(Env parent) {
        this(parent, 0);
    }

    public BlockScopeEnv(Env parent, int localsSize) {
        super(parent);
        if (localsSize <= 0) {
            locals = Collections.emptyMap();
            types = Collections.emptyMap();
        } else {
            locals = Maps.newHashMapWithExpectedSize(localsSize);
            types = Maps.newHashMapWithExpectedSize(localsSize);
        }
    }

    public void declareVariable(Class type, String name) {
        if (locals == Collections.EMPTY_MAP) {
            this.locals = new HashMap<String, Object>(2);
        }
        locals.put(name, null);

        if (types == null || types == Collections.EMPTY_MAP) {
            types = new HashMap<String, Class>(2);
        }
        types.put(name, type);
    }

    public Object getLocalVariable(String name) {
        if (locals.containsKey(name))
            return locals.get(name);
        else
            return parent.getLocalVariable(name);
    }

    /** Because might deserialize old version of class with null value for field */
    private Map<String, Class> getTypes() {
        if (types == null) {
            this.types = Collections.emptyMap();
        }
        return this.types;
    }

    public Class getLocalVariableType(String name) {
        return (locals.containsKey(name)) ? getTypes().get(name) : parent.getLocalVariableType(name);
    }

    public void setLocalVariable(String name, Object value) {
        if (locals.containsKey(name))
            locals.put(name,value);
        else
            parent.setLocalVariable(name, value);
    }

    private static final long serialVersionUID = 1L;
}
