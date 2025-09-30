package com.cloudbees.groovy.cps;

/**
 * Base class for {@link Block} that can come to the left hand side of an assignment, aka "l-value"
 *
 * Subtypes implement {@link #evalLValue(Env, Continuation)} that computes {@link LValue} object,
 * which provides read/write access.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class LValueBlock implements Block {
    /**
     * Evaluates to the value. Getter.
     */
    public final Next eval(Env e, Continuation k) {
        return asLValue().eval(e, new GetAdapter(k));
    }

    /**
     * Passes the {@link LValue#get(Continuation)} to the wrapped continuation
     */
    private static class GetAdapter implements Continuation {
        private final Continuation k;

        public GetAdapter(Continuation k) {
            this.k = k;
        }

        public Next receive(Object l) {
            return ((LValue) l).get(k);
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Evaluate the block as {@link LValue} and pass it to {@link Continuation} when done.
     *
     * <p>
     * {@link LValue} can then be used to set the value.
     */
    protected abstract Next evalLValue(Env e, Continuation k);

    /**
     * Obtains an {@link Block} that's equivalent to this block except it "returns"
     * an {@link LValue}.
     */
    public final Block asLValue() {
        return new BlockImpl();
    }

    private class BlockImpl implements Block {
        public Next eval(Env e, Continuation k) {
            return evalLValue(e, k);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
