package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Env;

/**
 * Attribute access expression like {@code foo.@bar}, which is an l-value.
 *
 * @author Kohsuke Kawaguchi
 */
public class AttributeAccessBlock extends PropertyishBlock<String> {
    public AttributeAccessBlock(SourceLocation loc, Block lhs, Block property) {
        super(loc, lhs, property);
    }

    protected Object rawGet(Env e, Object lhs, String name) throws Throwable {
        return e.getInvoker().getAttribute(lhs,false,false,name);
    }

    protected void rawSet(Env e, Object lhs, String name, Object v) throws Throwable {
        e.getInvoker().setAttribute(lhs,name,false,false,v);
    }

    @Override
    protected String coerce(Object name) {
        // TODO: verify the behaviour of Groovy if the property expression evaluates to non-String
        return name.toString();
    }

    private static final long serialVersionUID = 1L;
}
