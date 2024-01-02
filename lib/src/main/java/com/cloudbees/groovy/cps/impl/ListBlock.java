package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import org.codehaus.groovy.runtime.InvokerHelper;

/**
 * [a,b,c,d] to list.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListBlock extends CollectionLiteralBlock {
    public ListBlock(Block... args) {
        super(args);
    }

    @Override
    protected Object toCollection(Object[] result) {
        return InvokerHelper.createList(SpreadBlock.despreadList(result));
    }

    private static final long serialVersionUID = 1L;
}
