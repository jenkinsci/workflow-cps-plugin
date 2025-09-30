package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * Turns a list of {@link Block} into a collection object.
 *
 * Common part of {@link ListBlock} and {@link MapBlock}
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class CollectionLiteralBlock implements Block {
    /**
     * Arguments to evaluate.
     */
    private final Block[] argExps;

    public CollectionLiteralBlock(Block... args) {
        this.argExps = args;
    }

    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e, k).dispatch();
    }

    protected abstract Object toCollection(Object[] result);

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        Object[] list = new Object[argExps.length];
        int idx;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next item(Object v) {
            list[idx++] = v;
            return dispatch();
        }

        /**
         * If there are more arguments to evaluate, do so. Otherwise return the list.
         */
        private Next dispatch() {
            if (argExps.length > idx) return then(argExps[idx], e, item);
            else {
                return k.receive(toCollection(list));
            }
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr item = new ContinuationPtr(ContinuationImpl.class, "item");

    private static final long serialVersionUID = 1L;
}
