package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.LValue;
import com.cloudbees.groovy.cps.LValueBlock;
import com.cloudbees.groovy.cps.Next;

import static java.util.Collections.emptyList;

/**
 * Common part of {@link PropertyAccessBlock} and {@link AttributeAccessBlock}.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class PropertyishBlock extends LValueBlock {
    private final Block lhs, property;
    private final SourceLocation loc;

    public PropertyishBlock(SourceLocation loc, Block lhs, Block property) {
        this.loc = loc;
        this.lhs = lhs;
        this.property = property;
    }

    public Next evalLValue(final Env e, final Continuation k) {
        return new ContinuationImpl(e,k).then(lhs,e,fixLhs);
    }

    protected abstract Object rawGet(Env e, Object lhs, String name) throws Throwable;
    protected abstract void rawSet(Env e, Object lhs, String name, Object v) throws Throwable;


    class ContinuationImpl extends ContinuationGroup implements LValue {
        final Continuation k;
        final Env e;

        Object lhs;
        String name;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next fixLhs(Object lhs) {
            this.lhs = lhs;
            return then(property,e,fixName);
        }

        public Next fixName(Object name) {
            // TODO: verify the behaviour of Groovy if the property expression evaluates to non-String
            this.name = name.toString();

            return k.receive(this);
        }

        public Next get(Continuation k) {
            Object v;
            try {
                v = rawGet(e,lhs,name);
            } catch (Throwable t) {
                return throwException(e, t, loc, new ReferenceStackTrace());
            }

            if (v instanceof CpsFunction) {
                // if this is a workflow function, it'd return a CpsFunction object instead
                // of actually executing the function, so execute it in the CPS
                return ((CpsFunction)v).invoke(e, loc, lhs, emptyList(),k);
            } else {
                // if this was a normal property, we get the value as-is.
                return k.receive(v);
            }
        }


        public Next set(Object v, Continuation k) {
            // TODO: how to handle the case when a setter is a workflow method?

            try {
                rawSet(e,lhs,name,v);
            } catch (Throwable t) {
                return throwException(e, t, loc, new ReferenceStackTrace());
            }

            return k.receive(null);
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr fixLhs = new ContinuationPtr(ContinuationImpl.class,"fixLhs");
    static final ContinuationPtr fixName = new ContinuationPtr(ContinuationImpl.class,"fixName");

    private static final long serialVersionUID = 1L;
}

