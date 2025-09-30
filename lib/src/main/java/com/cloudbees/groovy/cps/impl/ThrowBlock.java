package com.cloudbees.groovy.cps.impl;

import static com.cloudbees.groovy.cps.impl.SourceLocation.*;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class ThrowBlock implements Block {
    private final SourceLocation loc;
    private final Block exp;
    /**
     * If true, {@link Exception#fillInStackTrace()} is used at the point of throwing
     * to overwrite the stack trace of the exception.
     */
    private final boolean fillStackTrace;

    public ThrowBlock(Block exp) {
        this(UNKNOWN, exp, false);
    }

    public ThrowBlock(SourceLocation loc, Block exp, boolean fillStackTrace) {
        this.loc = loc;
        this.exp = exp;
        this.fillStackTrace = fillStackTrace;
    }

    public Next eval(final Env e, Continuation unused) {
        return new Next(exp, e, new Continuation() {
            public Next receive(Object t) {
                if (t == null) {
                    t = new NullPointerException();
                }
                if (!(t instanceof Throwable)) {
                    t = new ClassCastException(t.getClass() + " cannot be cast to Throwable");
                }
                Throwable throwable = Throwable.class.cast(t);

                if (fillStackTrace) {
                    /*
                       CPS TRACE
                         this section contains a synthesized fake stack trace that shows the logical stack trace of the CPS code
                       ORIGINAL TRACE
                         this section contains the actual stack trace where 'throwable' was created
                    */

                    List<StackTraceElement> stack = new ArrayList<>();

                    stack.add((loc != null ? loc : UNKNOWN).toStackTrace());
                    e.buildStackTraceElements(stack, Integer.MAX_VALUE);
                    stack.add(Continuable.SEPARATOR_STACK_ELEMENT);
                    stack.addAll(List.of(throwable.getStackTrace()));

                    throwable.setStackTrace(stack.toArray(new StackTraceElement[stack.size()]));
                }

                Continuation v = e.getExceptionHandler(throwable.getClass());
                return v.receive(t);
            }
        });
    }

    private static final long serialVersionUID = 1L;
}
