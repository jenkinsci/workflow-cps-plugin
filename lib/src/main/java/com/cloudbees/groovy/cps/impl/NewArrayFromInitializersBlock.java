package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Builder;

/**
 * @see Builder#newArrayFromInitializers
 */
public class NewArrayFromInitializersBlock extends CollectionLiteralBlock {

    public NewArrayFromInitializersBlock(Block... args) {
        super(args);
    }

    @Override
    protected Object toCollection(Object[] result) {
        return result;
    }

    private static final long serialVersionUID = 1L;
}
