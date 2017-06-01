package org.jenkinsci.plugins.workflow.cps;

import org.jenkinsci.plugins.workflow.actions.ErrorAction;

/**
 * Exception used when failed to save {@link ErrorAction}.
 *
 * Will be stored in {@link ErrorAction}
 * instead of the original exception.
 *
 * @since 2.18
 */
public class ErrorActionException extends Exception {
    private static final long serialVersionUID = 1L;

    /**
     * ctor
     *
     * @param msg message
     * @param t root cause
     *
     * @see Exception#Exception(String, Throwable)
     */
    public ErrorActionException(String msg, Throwable t) {
        super(msg, t);
    }
}
