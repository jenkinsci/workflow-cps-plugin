package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import com.google.inject.Inject;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import java.util.Collections;
import java.util.Set;

/**
 * @author Kohsuke Kawaguchi
 * @see "doc/step-in-groovy.md"
 */
public abstract class GroovyStepDescriptor extends StepDescriptor {
    @Inject
    private GroovyStepExecutionCompiler compiler;

    @Override
    public Set<Class<?>> getRequiredContext() {
        return Collections.emptySet();
    }

    /**
     * Name of the {@link GroovyStepExecution} class written in Groovy,
     * packaged as a source file in the plugin.
     *
     * The default name is the step class name plus "Execution" by convention.
     */
    public String getExecutionClassName() {
        return clazz.getName()+"Execution";
    }
}
