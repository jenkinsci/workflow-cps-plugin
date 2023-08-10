package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import groovy.lang.SpreadMapEvaluatingException;
import org.codehaus.groovy.runtime.InvokerHelper;

public class SpreadMapBlock implements Block {
    private final SourceLocation loc;
    private final Block mapExp;

    public SpreadMapBlock(SourceLocation loc, Block mapExp) {
        this.loc = loc;
        this.mapExp = mapExp;
    }

    @Override
    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e, k).then(mapExp, e, fixMap);
    }

    class ContinuationImpl extends ContinuationGroup {
        private final Env e;
        private final Continuation k;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next fixMap(Object value) {
            try {
                // Creates a groovy.lang.SpreadMap, which InvokerHelper.createMap (used by MapBlock) handles specially.
                return k.receive(InvokerHelper.spreadMap(value));
            } catch (SpreadMapEvaluatingException t) {
                return throwException(e, t, loc, new ReferenceStackTrace());
            }
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr fixMap = new ContinuationPtr(ContinuationImpl.class, "fixMap");

    private static final long serialVersionUID = 1L;
}
