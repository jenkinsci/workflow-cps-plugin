package com.cloudbees.groovy.cps;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
class FunctionCallEnv implements Env {
    // TODO: How do we correctly assign local variables to its scope?

    // TODO: delegate?
    final Map<String,Object> locals = new HashMap<String, Object>();

    private final Continuation returnAddress;

    /**
     * Caller environment, used for throwing an excception.
     *
     * Can be null if there's no caller.
     */
    private final Env caller;

    /**
     * @param caller
     *      The environment of the call site. Can be null but only if the caller is outside CPS execution.
     */
    FunctionCallEnv(Env caller, Object _this, Continuation returnAddress) {
        this.caller = caller;
        this.returnAddress = returnAddress;
        locals.put("this",_this);
    }

    public void declareVariable(Class type, String name) {
        // no-op
    }

    public Object getLocalVariable(String name) {
        return locals.get(name);
    }

    public void setLocalVariable(String name, Object value) {
        locals.put(name,value);
    }

    public Continuation getReturnAddress() {
        return returnAddress;
    }

    public Continuation getExceptionHandler(Class<? extends Throwable> type) {
        if (caller==null) {
            // TODO: maybe define a mechanism so that the resume() or start() kinda method will return
            // by having this exception thrown?
            return Continuation.HALT;
        } else {
            // propagate the exception to the caller
            return caller.getExceptionHandler(type);
        }
    }
}
