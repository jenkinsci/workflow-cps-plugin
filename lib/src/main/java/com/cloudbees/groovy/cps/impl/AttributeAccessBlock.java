package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.sandbox.CallSiteTag;
import java.util.Collection;

/**
 * Attribute access expression like {@code foo.@bar}, which is an l-value.
 *
 * @author Kohsuke Kawaguchi
 */
public class AttributeAccessBlock extends PropertyishBlock {
    public AttributeAccessBlock(
            SourceLocation loc, Collection<CallSiteTag> tags, Block lhs, Block property, boolean safe) {
        super(loc, lhs, property, safe, tags);
    }

    protected Object rawGet(Env e, Object lhs, Object name) throws Throwable {
        return e.getInvoker().contextualize(this).getAttribute(lhs, coerce(name));
    }

    protected void rawSet(Env e, Object lhs, Object name, Object v) throws Throwable {
        e.getInvoker().contextualize(this).setAttribute(lhs, coerce(name), v);
    }

    private String coerce(Object name) {
        // TODO: verify the behaviour of Groovy if the property expression evaluates to non-String
        return name.toString();
    }

    private static final long serialVersionUID = 1L;
}
