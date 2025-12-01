package com.cloudbees.groovy.cps;

import groovy.lang.Closure;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.codehaus.groovy.runtime.GroovyCategorySupport;

/**
 * Remaining computation to execute. To work around the lack of tail-call optimization.
 *
 * The remaining computation is either to execute {@link #f} under {#link #e} and pass the
 * result to {@link #k} immediately, or to suspend execution by yielding {@link #yield},
 * and when the execution is resumed, continue by passing the resume value to {@link #k}
 * (or throw the resume value to the catch handler as specified by {@link #e}.)
 *
 * @author Kohsuke Kawaguchi
 */
public final class Next implements Serializable, Continuation {
    public final Block f;
    public final Env e;
    public final Continuation k;

    /**
     * If the program getting executed wants to yield a value and suspend its execution,
     * this value is set to non-null.
     *
     * This field and {@link #f} is mutually exclusive.
     */
    public final Outcome yield;

    public Next(Block f, Env e, Continuation k) {
        this.f = f;
        this.e = e;
        this.k = k;
        this.yield = null;
    }

    public Next(Env e, Continuation k, Outcome yield) {
        this.f = null;
        this.e = e;
        this.k = k;
        this.yield = yield;
        assert yield != null;
    }

    /**
     * Resumes the execution of this program state, until it yields a value or finishes computation.
     */
    public Next run() {
        Next n = this;
        while (n.yield == null) {
            n = n.step();
        }
        return n;
    }

    /** for testing only */
    public Outcome run(final int max) {
        return GroovyCategorySupport.use(Continuable.categories, new Closure<>(null) {
            @Override
            public Outcome call() {
                int remaining = max;
                List<String> functions = new ArrayList<>();
                Next n = Next.this;
                while (n.yield == null) {
                    functions.add(n.f.getClass().getCanonicalName());
                    if (--remaining == 0) {
                        int len = functions.size();
                        throw new AssertionError("Did not terminate; ran " + len + " steps ending with: "
                                + functions.subList(len - 20, len));
                    }
                    n = n.step();
                }
                return n.yield;
            }
        });
    }

    /**
     * Executes one step
     */
    public Next step() {
        return f.eval(e, k);
    }

    /**
     * Creates a {@link Next} object that
     * causes the interpreter loop to exit with the specified value, then optionally allow the interpreter
     * to resume with the specified {@link Continuation}.
     */
    public static Next yield(Object v, Env e, Continuation k) {
        return yield0(new Outcome(v, null), e, k);
    }

    /**
     * Creates a {@link Next} object that
     * causes the interpreter loop to exit with the specified value, then optionally allow the interpreter
     * to resume to the continuation represented by {@link Continuable}.
     */
    public static Next yield0(Outcome v, Continuable c) {
        return yield0(v, c.getE(), c.getK());
    }

    /**
     * Crestes a {@link Next} object that
     * causes the interpreter loop to keep evaluating the continuation represented by {@link Continuable}
     * by passing the outcome (or throwing it).
     */
    public static Next go0(Outcome v, Continuable c) {
        return v.resumeFrom(c.getE(), c.getK());
    }

    private static Next yield0(Outcome v, Env e, Continuation k) {
        if (v == null) throw new IllegalStateException("trying to yield null");

        return new Next(e, k, v);
    }

    /**
     * Creates a {@link Next} object that terminates the computation and either returns a value.
     */
    public static Next terminate(Object v) {
        return terminate0(new Outcome(v, null));
    }

    /**
     * Creates a {@link Next} object that terminates the computation by throwing an exception.
     */
    public static Next unhandledException(Throwable t) {
        return terminate0(new Outcome(null, t));
    }

    /**
     * Creates a {@link Next} object that terminates the computation and either returns a value or throw an exception.
     */
    public static Next terminate0(Outcome v) {
        return yield0(v, null, HALT);
    }

    /**
     * As a {@link Continuation}, just ignore the argument.
     */
    public Next receive(Object unused) {
        return this;
    }

    private static final long serialVersionUID = 1L;
}
