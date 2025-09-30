package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.LocalVariableBlock;
import com.cloudbees.groovy.cps.impl.VariableDeclBlock;
import com.cloudbees.groovy.cps.sandbox.Invoker;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.Closure;
import java.io.Serializable;
import java.util.List;

/**
 * Represents an environment in which {@link Block} is evaluated.
 *
 * In the <a href="https://en.wikipedia.org/wiki/Harvard_architecture">Harvard architecture</a> terms,
 * {@link Block} is instruction and {@link Env} is data.
 *
 * <p>
 * This interface is not to be implemented outside this library.
 *
 * <p>
 * See {@code cps-model.md}
 *
 * @author Kohsuke Kawaguchi
 */
public interface Env extends Serializable {
    /**
     * Defines a local variable in the current environment.
     *
     * This method is called when a variable declaration is encountered.
     *
     * @param type
     *      Type of the local variable. {@link Object} when unknown/implicit (e.g. "def x")
     * @param name
     *      Name of the local variable.
     * @see VariableDeclBlock
     */
    void declareVariable(@NonNull Class type, @NonNull String name);

    /**
     * Obtains the current value of a local variable in the current environment.
     * @param name
     *      Name of the local variable.
     * @see LocalVariableBlock
     */
    Object getLocalVariable(@NonNull String name);

    /**
     * Sets the local variable to a new value.
     * @param name
     *      Name of the local variable.
     * @param value
     *      New value
     * @see LocalVariableBlock
     */
    void setLocalVariable(@NonNull String name, Object value);

    @CheckForNull
    Class getLocalVariableType(@NonNull String name);

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
