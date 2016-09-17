package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import groovy.lang.GroovyResourceLoader;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Filtering {@link GroovyResourceLoader} that exposes {@link GroovyStepExecution} subtypes.
 *
 * @author Kohsuke Kawaguchi
 * @see GroovyShellDecoratorImpl
 * @see "doc/step-in-groovy.md"
 */
final class GroovyStepExecutionLoader implements GroovyResourceLoader {
    private final GroovyResourceLoader base;
    private final ClassLoader uberClassLoader;

    GroovyStepExecutionLoader(GroovyResourceLoader base) {
        this.base = base;
        this.uberClassLoader = Jenkins.getActiveInstance().getPluginManager().uberClassLoader;
    }

    @Override
    public URL loadGroovySource(String className) throws MalformedURLException {
        for (StepDescriptor d : StepDescriptor.all()) {
            if (className.equals(d.clazz.getName()+"Execution")) {
                String resName = className.replace('.', '/') + ".groovy";
                URL res = uberClassLoader.getResource(resName);
                if (res==null)
                    throw new IllegalStateException(d.clazz.getName()+" is missing its companion "+className);
                return res;
            }
        }
        return base.loadGroovySource(className);
    }
}
