package com.cloudbees.groovy.cps;

import java.util.List;

/**
 * Catch block in a try/catch statement.
 *
 * @author Kohsuke Kawaguchi
 * @see Builder#tryCatch(Expression, List)
 */
public class CatchExpression {
    /**
     * Type of the exception to catch.
     */
    public final Class<? extends Throwable> type;

    /**
     * Name of the variable that receives the exception.
     */
    public final String name;

    /**
     * Code that executes up on receiving an exception.
     */
    public final Expression handler;

    public CatchExpression(Class<? extends Throwable> type, String name, Expression handler) {
        this.name = name;
        this.handler = handler;
        this.type = type;
    }
}
