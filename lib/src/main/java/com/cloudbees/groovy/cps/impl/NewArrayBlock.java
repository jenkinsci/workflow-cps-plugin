package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import java.lang.reflect.Array;

/**
 * Multi-dimensional array instantiation like {@code new String[1][2][3]}
 *
 * @author Kohsuke Kawaguchi
 */
public class NewArrayBlock implements Block {
    private final Class componentType;
    private final Block[] dimensionExps;
    private final SourceLocation loc;

    public NewArrayBlock(SourceLocation loc, Class componentType, Block... dimensionExps) {
        this.loc = loc;
        this.componentType = componentType;
        this.dimensionExps = dimensionExps;
    }

    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e, k).dispatchOrArg();
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        int[] dimensions = new int[dimensionExps.length];
        int idx;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next fixArg(Object v) {
            try {
                dimensions[idx++] = (Integer) e.getInvoker().cast(v, int.class, false, false, false);
            } catch (Throwable t) {
                return throwException(e, t, loc, new ReferenceStackTrace());
            }
            return dispatchOrArg();
        }

        /**
         * If there are more arguments to evaluate, do so. Otherwise evaluate the function.
         */
        private Next dispatchOrArg() {
            if (dimensions.length > idx) return then(dimensionExps[idx], e, fixArg);
            else {
                // ready to instantiate
                Object v = Array.newInstance(componentType, dimensions);
                return k.receive(v);
            }
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr fixArg = new ContinuationPtr(ContinuationImpl.class, "fixArg");
}
