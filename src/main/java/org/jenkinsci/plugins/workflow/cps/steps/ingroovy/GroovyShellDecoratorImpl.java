package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import groovy.lang.GroovyShell;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;

import javax.annotation.CheckForNull;
import javax.inject.Inject;

/**
 * Insert Groovy content roots into trusted shell.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class GroovyShellDecoratorImpl extends GroovyShellDecorator {
    @Inject
    GroovyCompiler compiler;

    @Override
    public GroovyShellDecorator forTrusted() {
        return new GroovyShellDecorator() {
            @Override
            public void configureShell(@CheckForNull CpsFlowExecution context, GroovyShell shell) {
                compiler.addContentRootsTo(shell);
            }
        };
    }
}
