package com.cloudbees.groovy.cps;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link Env} for a new block.
 *
 * @author Kohsuke Kawaguchi
 */
class BlockScopeEnv extends ProxyEnv {
    private final Map<String,Object> locals = new HashMap<String, Object>();

    public BlockScopeEnv(Env parent) {
        super(parent);
    }

    public void declareVariable(String name) {
        locals.put(name,null);
    }

    public Object getLocalVariable(String name) {
        if (locals.containsKey(name))
            return locals.get(name);
        else
            return parent.getLocalVariable(name);
    }

    public void setLocalVariable(String name, Object value) {
        if (locals.containsKey(name))
            locals.put(name, value);
        else
            parent.setLocalVariable(name, value);
    }
}
