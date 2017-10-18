package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.DepthTrackingEnv;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

import java.util.List;

/**
 * Represents a CPS-transformed function.
 *
 * @author Kohsuke Kawaguchi
 */
public class CpsFunction extends CpsCallable {
    public CpsFunction(List<String> parameters, Block body) {
        super(parameters, body);
    }

    public Next invoke(Env caller, SourceLocation loc, Object receiver, List<?> args, Continuation k) {
        Env e = new FunctionCallEnv(caller, k, loc, receiver, args.size());
        assignArguments(args,e);

        // 'e' will be a DepthTrackingEnv for all pipelines begun with this version of the library
        // but if we upgrade groovy-cps and resume older pipelines, they won't be.
        if (e instanceof DepthTrackingEnv && ((DepthTrackingEnv)e).getDepth() > DepthTrackingEnv.MAX_LEGAL_DEPTH) {
            StackOverflowError err;
            int depth = ((DepthTrackingEnv) e).getDepth();
            if (loc != null) {
                err = new StackOverflowError("Excessively nested closures/functions at "+loc+" - look for unbounded recursion - call depth: "+depth);
            } else {
                err = new StackOverflowError("Excessively nested closures/functions - look for unbounded recursion - call depth: "+depth);
            }
            return e.getExceptionHandler(StackOverflowError.class).receive(err);
        }
        return new Next(body, e, k);
    }

    private static final long serialVersionUID = 1L;
}
