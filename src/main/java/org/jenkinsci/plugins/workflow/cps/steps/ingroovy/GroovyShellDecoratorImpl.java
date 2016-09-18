package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import com.google.inject.Inject;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;

/**
 * Expose {@link GroovyStepExecution} impls to trusted classloader by inserting it as a parent
 * of a trusted classloader.
 *
 * @see "doc/step-in-groovy.md"
 * @author Kohsuke Kawaguchi
 */
@Extension
public class GroovyShellDecoratorImpl extends GroovyShellDecorator {
    @Inject
    private GroovyStepExecutionCompiler compiler;

    @Override
    public GroovyShellDecorator forTrusted() {
        return new GroovyShellDecorator() {
            @Override
            public ClassLoader decorateParent(ClassLoader parent) {
                return compiler.createShell(parent).getClassLoader();
            }
        };
    }
}
