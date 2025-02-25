package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.DepthTrackingEnv;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.sandbox.Invoker;
import com.google.common.collect.Maps;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Common part between {@link FunctionCallEnv} and {@link ClosureCallEnv}.
 *
 * @author Kohsuke Kawaguchi
 */
/*package*/ abstract class CallEnv implements DepthTrackingEnv {
    private final Continuation returnAddress;

    /** To conserve memory, lazily declared using {@link Collections#EMPTY_MAP} until we declare variables, then converted to a (small) {@link HashMap} */
    private Map<String, Class> types;

    /**
     * Caller environment, used for throwing an exception.
     *
     * Can be null if there's no caller.
     */
    private final Env caller;

    /**
     * Source location of the call site.
     */
    @CheckForNull
    private final SourceLocation callSiteLoc;

    private Invoker invoker;

    int depth;

    /**
     * @param caller
     *      The environment of the call site. Can be null but only if the caller is outside CPS execution.
     */
    public CallEnv(Env caller, Continuation returnAddress, SourceLocation loc) {
        this(caller, returnAddress, loc, 1);
    }

    public CallEnv(Env caller, Continuation returnAddress, SourceLocation loc, int localsCount) {
        this.caller = caller;
        this.returnAddress = returnAddress;
        this.callSiteLoc = loc;
        this.invoker = caller==null ? Invoker.INSTANCE : caller.getInvoker();
        assert returnAddress!=null;
        if (localsCount <= 0) {
            types = Collections.EMPTY_MAP;
        } else {
            types = Maps.newHashMapWithExpectedSize(localsCount);
        }
        depth = (caller instanceof DepthTrackingEnv) ? ((DepthTrackingEnv) caller).getDepth() + 1 : 1;
    }

    /** Because might deserialize old version of class with null value for field */
    protected Map<String, Class> getTypes() {
        if (types == null) {
            this.types = Collections.EMPTY_MAP;
        }
        return this.types;
    }

    /** Used when we are actually going to mutate the types info */
    protected Map<String,Class> getTypesForMutation() {
        if (types == null || types == Collections.EMPTY_MAP) {
            this.types = new HashMap<>(2);
        }
        return this.types;
    }

    public Class getLocalVariableType(String name) {
        return getTypes().get(name);
    }

    /**
     * Sets the {@link Invoker}, which gets inherited through the call chain.
     */
    public void setInvoker(Invoker invoker) {
        this.invoker = invoker;
    }

    public Invoker getInvoker() {
        return invoker;
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

    public void buildStackTraceElements(List<StackTraceElement> stack, int depth) {
        if (callSiteLoc!=null)
            stack.add(callSiteLoc.toStackTrace());
        if (caller!=null && depth>1)
            caller.buildStackTraceElements(stack, depth-1);
    }

    private static final long serialVersionUID = 1L;

    public int getDepth() {
        return depth;
    }

    @Override
    public String toString() {
        return callSiteLoc != null ? super.toString() + " @" + callSiteLoc : super.toString();
    }

}
