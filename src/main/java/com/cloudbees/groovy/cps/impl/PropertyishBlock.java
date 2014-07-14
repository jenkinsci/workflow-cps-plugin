package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.LValue;
import com.cloudbees.groovy.cps.LValueBlock;
import com.cloudbees.groovy.cps.Next;

/**
 * Common part of {@link PropertyAccessBlock} and {@link AttributeAccessBlock}.
 *
 * @param <T>
 *      type that the property expression evaluates to. String for properties/attributes and int for arrays.
 * @author Kohsuke Kawaguchi
 */
abstract class PropertyishBlock<T> extends LValueBlock {
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

    // invoke the underlying Groovy object. Main point of attribute/property handling difference.
    protected abstract Object rawGet(Env e, Object lhs, T property) throws Throwable;
    protected abstract void rawSet(Env e, Object lhs, T property, Object v) throws Throwable;

    /**
     * Given the result of the property/attribute name or array index value evaluation,
     * coerce the result into the right type.
     */
    protected abstract T coerce(Object property);

    class ContinuationImpl extends ContinuationGroup implements LValue {
        final Continuation k;
        final Env e;

        Object lhs;
        T name;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next fixLhs(Object lhs) {
            this.lhs = lhs;
            return then(property,e,fixName);
        }

        public Next fixName(Object name) {
            this.name = coerce(name);
            return k.receive(this);
        }

        public Next get(Continuation k) {
            try {
                Object v = rawGet(e,lhs,name);
                // if this was a normal property, we get the value as-is.
                return k.receive(v);
            } catch (CpsCallableInvocation inv) {
                // if this was a workflow function, execute it in the CPS
                return inv.invoke(e, loc, k);
            } catch (Throwable t) {
                return throwException(e, t, loc, new ReferenceStackTrace());
            }
        }


        public Next set(Object v, Continuation k) {
            try {
                rawSet(e,lhs,name,v);
                return k.receive(null);
            } catch (CpsCallableInvocation inv) {
                // if this was a workflow function, execute it in the CPS
                return inv.invoke(e, loc, k);
            } catch (Throwable t) {
                return throwException(e, t, loc, new ReferenceStackTrace());
            }

        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;

    static final ContinuationPtr fixLhs = new ContinuationPtr(ContinuationImpl.class,"fixLhs");
    static final ContinuationPtr fixName = new ContinuationPtr(ContinuationImpl.class,"fixName");
}

