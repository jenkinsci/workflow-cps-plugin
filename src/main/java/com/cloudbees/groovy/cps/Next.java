package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.Conclusion;
import com.cloudbees.groovy.cps.impl.YieldContinuation;

import java.io.Serializable;

import static com.cloudbees.groovy.cps.Continuation.*;

/**
 * Remaining computation to execute. To work around the lack of tail-call optimization
 *
 * @author Kohsuke Kawaguchi
 */
public class Next implements Serializable {
    Block f;
    Env e;
    Continuation k;

    /**
     * If the program getting executed wants to yield a value and suspend its execution,
     * this value is set to non-null.
     */
    /*package*/ Conclusion yield;
    /*package*/ Resumable resumable;

    public Next(Block f, Env e, Continuation k) {
        this.f = f;
        this.e = e;
        this.k = k;
    }

    /**
     * Resumes the execution of this program state, until it yields a value or finishes computation.
     */
    public Next run() {
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

    /**
     * Creates a {@link Next} object that
     * causes the interpreter loop to exit with the specified value, then optionally allow the interpreter
     * to resume with the specified {@link Continuation}.
     */
    public static Next yield(Conclusion v, Resumable r) {
        if (v==null)        throw new IllegalStateException("trying to yield null");

        Next n = new Next(null,null,null);
        n.yield = v;
        n.resumable = r;

        return n;
    }

    /**
     * Creates a {@link Next} object that terminates the computation and either returns a value or throw an exception.
     */
    public static Next terminate(Conclusion v) {
        return yield(v, null);
    }

    /**
     * Returns this object as a {@link Continuation} that ignores the argument.
     */
    public Resumable asResumable() {
        if (isEnd())    return null;   // so that the caller can tell when it has terminated.
        else            return new YieldContinuation(e,new ConstContinuation());
    }

    /**
     * Does this represent the end of the program?
     */
    public boolean isEnd() {
        return k== HALT && e==Block.NOOP;
    }

    private class ConstContinuation implements Continuation {
        public Next receive(Object o) {
            return Next.this;
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
