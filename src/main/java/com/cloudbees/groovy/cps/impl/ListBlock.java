package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

import java.util.ArrayList;
import java.util.List;

/**
 * [a,b,c,d] kind of block.
 *
 * @author Kohsuke Kawaguchi
 */
public class ListBlock implements Block {
    /**
     * Arguments to the list.
     */
    private final Block[] argExps;

    public ListBlock(Block... args) {
        this.argExps = args;
    }

    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e,k).dispatch();
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        List<Object> list = new ArrayList<Object>();
        int idx;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next item(Object v) {
            list.add(v);
            return dispatch();
        }

        /**
         * If there are more arguments to evaluate, do so. Otherwise return the list.
         */
        private Next dispatch() {
            if (argExps.length>idx)
                return then(argExps[idx++],e,item);
            else {
                return k.receive(list);
            }
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr item = new ContinuationPtr(ContinuationImpl.class,"item");

    private static final long serialVersionUID = 1L;
}
