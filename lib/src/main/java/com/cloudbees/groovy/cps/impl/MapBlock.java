package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import org.codehaus.groovy.runtime.InvokerHelper;

/**
 * [a:b, c:d, e:f, ...]
 *
 * @author Kohsuke Kawaguchi
 */
public class MapBlock extends CollectionLiteralBlock {
    public MapBlock(Block... args) {
        super(args);
    }

    @Override
    protected Object toCollection(Object[] result) {
        return InvokerHelper.createMap(result);
    }

    private static final long serialVersionUID = 1L;
}
