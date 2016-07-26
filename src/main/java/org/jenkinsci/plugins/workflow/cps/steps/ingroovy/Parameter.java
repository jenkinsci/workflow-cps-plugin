package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import org.jenkinsci.plugins.workflow.cps.steps.ingroovy.StepInGroovy.StepDescriptorInGroovy;

/**
 * Reflection information of a method call parameter.
 *
 * @author Kohsuke Kawaguchi
 * @see StepDescriptorInGroovy#getParameters()
 */
public final class Parameter {
    public final Class type;
    public final String name;

    /*package*/ Parameter(Class type, String name) {
        this.type = type;
        this.name = name;
    }
}
