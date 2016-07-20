package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

/**
 * @author Kohsuke Kawaguchi
 */
public final class Parameter {
    public final Class type;
    public final String name;

    public Parameter(Class type, String name) {
        this.type = type;
        this.name = name;
    }
}
