package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.LValue;
import com.cloudbees.groovy.cps.LValueBlock;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.sandbox.CallSiteTag;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.Collections;

/**
 * Common part of {@link PropertyAccessBlock} and {@link AttributeAccessBlock}.
 *
 * @author Kohsuke Kawaguchi
 */
abstract class PropertyishBlock extends LValueBlock implements CallSiteBlock, Block {
    private final Collection<CallSiteTag> tags; // can be null for instances deserialized from the old form
    private final Block lhs, property;
    private final SourceLocation loc;
    private final boolean safe;

    public PropertyishBlock(SourceLocation loc, Block lhs, Block property, boolean safe, Collection<CallSiteTag> tags) {
        this.loc = loc;
        this.lhs = lhs;
        this.property = property;
        this.safe = safe;
        this.tags = tags;
    }

    @NonNull
    public Collection<CallSiteTag> getTags() {
        return tags != null ? Collections.unmodifiableCollection(tags) : Collections.<CallSiteTag>emptySet();
    }

    public Next evalLValue(final Env e, final Continuation k) {
        return new ContinuationImpl(e, k).then(lhs, e, fixLhs);
    }

    // invoke the underlying Groovy object. Main point of attribute/property handling difference.
    protected abstract Object rawGet(Env e, Object lhs, Object property) throws Throwable;

    protected abstract void rawSet(Env e, Object lhs, Object property, Object v) throws Throwable;

    class ContinuationImpl extends ContinuationGroup implements LValue {
        final Continuation k;
        final Env e;

        Object lhs;
        Object name;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next fixLhs(Object lhs) {
            this.lhs = lhs;
            return then(property, e, fixName);
        }

        public Next fixName(Object name) {
            this.name = name;
            return k.receive(this);
        }

        public Next get(Continuation k) {
            if (safe && lhs == null) {
                return k.receive(null);
            }
            try {
                Object v = rawGet(e, lhs, name);
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
                rawSet(e, lhs, name, v);
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

    static final ContinuationPtr fixLhs = new ContinuationPtr(ContinuationImpl.class, "fixLhs");
    static final ContinuationPtr fixName = new ContinuationPtr(ContinuationImpl.class, "fixName");
}
