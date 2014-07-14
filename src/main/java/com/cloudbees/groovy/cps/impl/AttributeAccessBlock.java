package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Env;

/**
 * Attribute access expression like {@code foo.@bar}, which is an l-value.
 *
 * @author Kohsuke Kawaguchi
 */
public class AttributeAccessBlock extends PropertyishBlock {
    public AttributeAccessBlock(SourceLocation loc, Block lhs, Block property) {
        super(loc, lhs, property);
    }

    protected Object rawGet(Env e, Object lhs, String name) throws Throwable {
        return e.getInvoker().getProperty(lhs,false,false,name);
    }

    protected void rawSet(Env e, Object lhs, String name, Object v) throws Throwable {
        e.getInvoker().setProperty(lhs,name,false,false,v);
    }

    private static final long serialVersionUID = 1L;
}
