package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;

import java.io.IOException;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;

/**
 * Base class that defines contract for steps written in Groovy.
 *
 * @author Kohsuke Kawaguchi
 * @see GroovyCompiler
 */
@PersistIn(PROGRAM)
public abstract class StepInGroovyScript extends CpsScript {
    public StepInGroovyScript() throws IOException {
    }

    public abstract String getDisplayName();
}
