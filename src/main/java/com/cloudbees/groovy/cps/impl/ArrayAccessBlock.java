package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Env;

/**
 * Array access like {@code x[3]} or {@code x['foo']}
 *
 * @author Kohsuke Kawaguchi
 */
public class ArrayAccessBlock extends PropertyishBlock<Object> {
    public ArrayAccessBlock(SourceLocation loc, Block lhs, Block property) {
        super(loc, lhs, property);
    }

    @Override
    protected Object rawGet(Env e, Object lhs, Object index) throws Throwable {
        return e.getInvoker().getArray(lhs, index);
    }

    @Override
    protected void rawSet(Env e, Object lhs, Object index, Object v) throws Throwable {
        e.getInvoker().setArray(lhs, index, v);
    }

    @Override
    protected Object coerce(Object index) {
        return index;
    }
}
