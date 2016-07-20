package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import groovy.lang.Binding;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import hudson.Extension;
import jenkins.model.Jenkins;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.jenkinsci.plugins.workflow.cps.steps.ingroovy.StepInGroovy.StepDescriptorInGroovy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;

/**
 * Used to compile Groovy step implementation to be able to reflect on its properties.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
@Restricted(NoExternalUse.class) // SezPoz demands that this is public, but this is an implementation detail in this plugin
public class GroovyCompiler {
    private final GroovyShell sh;

    public GroovyCompiler() {
        CompilerConfiguration cc = new CompilerConfiguration();
        cc.setScriptBaseClass(StepInGroovyScript.class.getName());
        sh = new GroovyShell(Jenkins.getActiveInstance().getPluginManager().uberClassLoader, new Binding(), cc);
    }

    /*package*/ Class compile(StepDescriptorInGroovy d) throws IOException {
        return sh.getClassLoader().parseClass(new GroovyCodeSource(d.getSourceFile()));
    }

    /*package*/ StepInGroovyScript parse(StepDescriptorInGroovy d) throws IOException {
        return (StepInGroovyScript)sh.parse(new GroovyCodeSource(d.getSourceFile()));
    }

    public static GroovyCompiler get() {
        return Jenkins.getActiveInstance().getInjector().getInstance(GroovyCompiler.class);
    }
}
