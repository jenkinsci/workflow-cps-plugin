package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

/**
 * Checked exception that we'll throw around to make sure
 * no strange wrapping happens.
 *
 * @author Kohsuke Kawaguchi
 * @see GroovyStepTest#exception()
 */
public class LifeIsToughException extends Exception {
    public LifeIsToughException(String message) {
        super(message);
    }
}
