package com.cloudbees.groovy.cps;

/**
 * Remaining computation to execute. To work around the lack of tail-call optimization
 *
 * @author Kohsuke Kawaguchi
 */
public class Next {
    Block f;
    Env e;
    Continuation k;

    /**
     * If the program getting executed wants to yield a value and suspend its execution,
     * this value is set to non-null.
     *
     * {@link #NULL} is used to yield null.
     */
    private Object yield;

    public Next(Block f, Env e, Continuation k) {
        this.f = f;
        this.e = e;
        this.k = k;
    }

    /**
     * Resumes the execution of this program state, until it yields a value or finishes computation.
     */
    public Next resume() {
        Next n = this;
        do {
            n = n.step();
        } while(n.yield==null);
        return n;
    }

    /**
     * Executes one step
     */
    public Next step() {
        return f.eval(e,k);
    }

    /*package*/ void yield(Object v) {
        if (v==null)  v = NULL;
        this.yield = v;
    }

    /*package*/ Object yieldedValue() {
        if (yield==NULL)    return null;
        return yield;
    }

    /**
     * Returns this object as a {@link Continuation} that ignores the argument.
     */
    public Continuation asContinuation() {
        if (isEnd())    return Continuation.HALT;   // so that the caller can tell when it has terminated.
        else            return new ConstContinuation();
    }

    /**
     * Does this represent the end of the program?
     */
    public boolean isEnd() {
        return k==Continuation.HALT && e==Block.NOOP;
    }

    private static final class Null {}
    private static final Null NULL = new Null();

    private class ConstContinuation implements Continuation {
        public Next receive(Object o) {
            return Next.this;
        }

        private static final long serialVersionUID = 1L;
    }
}
