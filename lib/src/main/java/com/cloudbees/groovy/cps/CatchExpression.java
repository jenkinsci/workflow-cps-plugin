package com.cloudbees.groovy.cps;

import java.io.Serializable;
import java.util.List;

/**
 * Catch block in a try/catch statement.
 *
 * @author Kohsuke Kawaguchi
 * @see Builder#tryCatch(Block, List)
 */
public class CatchExpression implements Serializable {
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
    public final Block handler;

    public CatchExpression(Class<? extends Throwable> type, String name, Block handler) {
        this.name = name;
        this.handler = handler;
        this.type = type;
    }

    private static final long serialVersionUID = 1L;
}
