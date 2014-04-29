package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Common part between {@link FunctionCallEnv} and {@link ClosureCallEnv}.
 *
 * @author Kohsuke Kawaguchi
 */
/*package*/ abstract class CallEnv implements Env {
    private final Continuation returnAddress;

    /**
     * Caller environment, used for throwing an exception.
     *
     * Can be null if there's no caller.
     */
    private final Env caller;

    /**
     * Source location of the call site.
     */
    @Nullable
    private final SourceLocation callSiteLoc;

    /**
     * @param caller
     *      The environment of the call site. Can be null but only if the caller is outside CPS execution.
     */
    public CallEnv(Env caller, Continuation returnAddress, SourceLocation loc) {
        this.caller = caller;
        this.returnAddress = returnAddress;
        this.callSiteLoc = loc;
        assert returnAddress!=null;
    }

    public final Continuation getReturnAddress() {
        return returnAddress;
    }

    public final Continuation getBreakAddress(String label) {
        throw new IllegalStateException("unexpected break statement");
    }

    public final Continuation getContinueAddress(String label) {
        throw new IllegalStateException("unexpected continue statement");
    }

    public final Continuation getExceptionHandler(Class<? extends Throwable> type) {
        if (caller==null) {
            // TODO: maybe define a mechanism so that the run() or start() kinda method will return
            // by having this exception thrown?
            return new Continuation() {
                public Next receive(Object o) {
                    return Next.unhandledException((Throwable)o);
                }
            };
        } else {
            // propagate the exception to the caller
            return caller.getExceptionHandler(type);
        }
    }

    public void buildStackTraceElements(List<StackTraceElement> stack) {
        if (callSiteLoc!=null)
            stack.add(callSiteLoc.toStackTrace());
        caller.buildStackTraceElements(stack);
    }

    private static final long serialVersionUID = 1L;
}
