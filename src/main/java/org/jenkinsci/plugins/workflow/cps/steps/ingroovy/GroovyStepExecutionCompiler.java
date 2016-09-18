package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyShell;
import hudson.Extension;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsGroovyShellFactory;

/**
 * Used to compile CPS-transformed code that does not run in the sandbox,
 * but unlike {@linkplain CpsFlowExecution#getTrustedShell() trusted shell}, code
 * loaded here will not automatically become visible to untrusted code.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
public class GroovyStepExecutionCompiler {
//    private GroovyShell sh;
//
//    /*package*/ synchronized GroovyShell sh() {
//        if (sh==null) {
//            sh = new CpsGroovyShellFactory(null).build();
//            GroovyClassLoader cl = sh.getClassLoader();
//            cl.setResourceLoader(new GroovyStepExecutionLoader(cl.getResourceLoader()));
//        }
//        return sh;
//    }

    /**
     * We want CPS transformed code, but without sandbox transformation, so that
     * calls from this classloader to any class in Jenkins will be fine, but untrusted code
     * cannot call methods on these classes unless explicitly whitelisted.
     */
    /*package*/ GroovyShell createShell(ClassLoader parent) {
        GroovyShell sh = new CpsGroovyShellFactory(null).withParent(parent).build();
        GroovyClassLoader cl = sh.getClassLoader();
        cl.setResourceLoader(new GroovyStepExecutionLoader(cl.getResourceLoader()));
        return sh;
    }

//    /*package*/ GroovyClassLoader getClassLoader() {
//        return sh().getClassLoader();
//    }

    public static GroovyStepExecutionCompiler get() {
        return Jenkins.getActiveInstance().getInjector().getInstance(GroovyStepExecutionCompiler.class);
    }
}
