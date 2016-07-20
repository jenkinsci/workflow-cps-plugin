package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import groovy.lang.Script;

/**
 * Base class that defines conract for steps written in Groovy.
 * @author Kohsuke Kawaguchi
 * @see GroovyCompiler
 */
public abstract class StepInGroovyScript extends Script {
    public abstract String getDisplayName();
}
