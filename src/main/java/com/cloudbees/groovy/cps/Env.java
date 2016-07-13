package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.sandbox.Invoker;
import groovy.lang.Closure;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.List;

/**
 * For variable lookup. This is local variables.
 *
 * @author Kohsuke Kawaguchi
 */
public interface Env extends Serializable {
    void declareVariable(Class type, String name);

    Object getLocalVariable(String name);
    void setLocalVariable(String name, Object value);

    @CheckForNull
    Class getLocalVariableType(@Nonnull String name);

    /**
     * Closure instance or 'this' object that surrounds the currently executing code.
     * 
     * <p> 
     * If a new closure instantiation is encountered, this is the object that becomes
     * {@linkplain Closure#getOwner() the owner} of that closure.
     * 
     * <p>
     * Dynamic property access inside closures are also resolved against this instance.
     */
    Object closureOwner();


    /**
     * Where should the return statement return to?
     */
    Continuation getReturnAddress();

    /**
     * If we see a break statement, where should we go?
     *
     * @param label
     *      Specifies the loop to break from. null for nearest loop.
     * @return
     *      For semantically correct Groovy code, the return value is never null, because not having the matching label
     *      is a compile-time error.
     */
    Continuation getBreakAddress(String label);

    /**
     * If we see a continue statement, where should we go?
     *
     * @param label
     *      Specifies the loop to repeat. null for nearest loop.
     * @return
     *      For semantically correct Groovy code, the return value is never null, because not having the matching label
     *      is a compile-time error.
     */
    Continuation getContinueAddress(String label);

    /**
     * Finds the exception handler that catches a {@link Throwable} instance of this type.
     *
     * @return
     *      never null. Even if there's no user-specified exception handler, the default 'unhandled exception handler'
     *      must be returned.
     */
    Continuation getExceptionHandler(Class<? extends Throwable> type);

    /**
     * Builds the current call stack information for {@link Throwable#getStackTrace()}.
     *
     * @param depth
     *      Maximum depth of stack trace to obtain.
     */
    void buildStackTraceElements(List<StackTraceElement> stack, int depth);

    /**
     * {@link Invoker} is typically scoped at the whole execution.
     *
     * @return never null
     */
    Invoker getInvoker();
}
