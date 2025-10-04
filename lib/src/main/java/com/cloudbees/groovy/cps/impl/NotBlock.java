package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

/**
 * !b
 *
 * @author Kohsuke Kawaguchi
 */
public class NotBlock implements Block {
    private final Block b;

    public NotBlock(Block b) {
        this.b = b;
    }

    @Override
    @SuppressFBWarnings(
            value = "DLS_DEAD_LOCAL_STORE",
            justification =
                    "Unused anonymous class exists to maintain compatibility with classes serialized before ContinuationImpl was introduced.")
    public Next eval(final Env e, final Continuation k) {
        Continuation backwardsCompatibility = new Continuation() {
            private static final long serialVersionUID = -7345620782904277090L;

            public Next receive(Object o) {
                // "e" is null in deserialized instances of this class, so we cannot use `e.getInvoker().cast(...)`.
                // That said, there are no known security issues with boolean casts, so this should be fine.
                boolean b = DefaultTypeTransformation.castToBoolean(o);
                return k.receive(!b);
            }
        };
        return new ContinuationImpl(e, k).then(b, e, cast);
    }

    @SuppressFBWarnings(value = "SIC_INNER_SHOULD_BE_STATIC", justification = "Too late to fix compatibly.")
    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next cast(Object o) {
            return castToBoolean(o, e, b -> k.receive(!b));
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr cast = new ContinuationPtr(ContinuationImpl.class, "cast");

    private static final long serialVersionUID = 1L;
}
