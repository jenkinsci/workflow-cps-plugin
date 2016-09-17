package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyResourceLoader;
import groovy.lang.GroovyShell;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;

import javax.annotation.CheckForNull;

/**
 * Expose GroovyStepExecution impls to trusted classloader.
 *
 * <p>
 * Classes visible in the trusted shell are visible to untrusted Jenkinsfile,
 * so we can't just put everything in uber classloader. To be able to selectively expose
 * specific Groovy files, we insert custom {@link GroovyResourceLoader}.
 *
 * @see "doc/step-in-groovy.md"
 * @see GroovyStepExecutionLoader
 * @author Kohsuke Kawaguchi
 */
@Extension
public class GroovyShellDecoratorImpl extends GroovyShellDecorator {
    @Override
    public GroovyShellDecorator forTrusted() {
        return new GroovyShellDecorator() {
            @Override
            public void configureShell(@CheckForNull CpsFlowExecution context, GroovyShell shell) {
                GroovyClassLoader cl = shell.getClassLoader();
                cl.setResourceLoader(new GroovyStepExecutionLoader(cl.getResourceLoader()));
            }
        };
    }
}
