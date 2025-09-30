package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.DepthTrackingEnv;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import java.util.List;

/**
 * Represents an invokable CPS-transformed closure.
 *
 * @author Kohsuke Kawaguchi
 */
class CpsClosureDef extends CpsCallable {
    /**
     * Environment that was captured as of closure instantiation.
     */
    private final Env capture;

    private final CpsClosure self;

    CpsClosureDef(List<String> parameters, Block body, Env capture, CpsClosure self) {
        super(parameters, body);
        this.capture = capture;
        this.self = self;
    }

    @Override
    Next invoke(Env caller, SourceLocation loc, Object receiver, List<?> args, Continuation k) {
        ClosureCallEnv e = new ClosureCallEnv(caller, k, loc, capture, self, args.size());

        assignArguments(args, e);

        // TODO: who handles 'it' ?

        if (e.getDepth() > DepthTrackingEnv.MAX_LEGAL_DEPTH) {
            StackOverflowError err;
            if (loc != null) {
                err = new StackOverflowError("Excessively nested closures/functions at " + loc
                        + " - look for unbounded recursion - call depth: " + e.getDepth());
            } else {
                err = new StackOverflowError(
                        "Excessively nested closures/functions - look for unbounded recursion - call depth: "
                                + e.getDepth());
            }
            return e.getExceptionHandler(StackOverflowError.class).receive(err);
        }

        return new Next(body, e, k);
    }

    private static final long serialVersionUID = 1L;
}
