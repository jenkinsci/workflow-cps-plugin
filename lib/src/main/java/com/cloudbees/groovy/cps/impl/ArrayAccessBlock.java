package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.sandbox.CallSiteTag;
import java.util.Collection;

/**
 * Array access like {@code x[3]} or {@code x['foo']}
 *
 * @author Kohsuke Kawaguchi
 */
public class ArrayAccessBlock extends PropertyishBlock {
    public ArrayAccessBlock(SourceLocation loc, Collection<CallSiteTag> tags, Block lhs, Block property) {
        super(loc, lhs, property, false, tags);
    }

    @Override
    protected Object rawGet(Env e, Object lhs, Object index) throws Throwable {
        return e.getInvoker().contextualize(this).getArray(lhs, index);
    }

    @Override
    protected void rawSet(Env e, Object lhs, Object index, Object v) throws Throwable {
        e.getInvoker().contextualize(this).setArray(lhs, index, v);
    }
}
