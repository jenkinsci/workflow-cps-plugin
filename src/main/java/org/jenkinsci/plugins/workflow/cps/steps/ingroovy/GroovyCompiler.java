package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.Extension;
import hudson.PluginWrapper;
import jenkins.model.Jenkins;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ASTTransformationCustomizer;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.steps.ingroovy.StepInGroovy.StepDescriptorInGroovy;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Used to compile Groovy step implementation to be able to reflect on its properties.
 *
 * @author Kohsuke Kawaguchi
 */
@Extension
@Restricted(NoExternalUse.class) // SezPoz demands that this is public, but this is an implementation detail in this plugin
public class GroovyCompiler {
    /**
     * Used to compile steps in Groovy for introspection.
     *
     * When actually executed in Jenkins Pipeline, {@link org.jenkinsci.plugins.workflow.cps.CpsGroovyShell} is used
     * and the code will be recompiled in each {@link CpsFlowExecution}.
     */
    private GroovyShell sh;

    // debug hook to insert additional content roots
    /*package*/ static final List<URL> additionalContentRoots = new ArrayList<>();

    private synchronized GroovyShell sh() {
        if (sh==null) {
            CompilerConfiguration cc = new CompilerConfiguration();
            cc.setScriptBaseClass(StepInGroovyScript.class.getName());

            // needed to introspect the compiled step
            cc.addCompilationCustomizers(new ASTTransformationCustomizer(new ParameterNameCaptureTransformation()));

            sh = new GroovyShell(Jenkins.getActiveInstance().getPluginManager().uberClassLoader, new Binding(), cc);

            // register all the content roots
            addContentRootsTo(sh);
        }
        return sh;
    }

    /*package*/ Class compile(StepDescriptorInGroovy d) throws ClassNotFoundException {
        return sh().getClassLoader().loadClass(d.getClassName());
    }

    /*package*/ StepInGroovyScript parse(StepDescriptorInGroovy d) throws IOException {
        try {
            return (StepInGroovyScript)compile(d).newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IOException("Failed to instantiate "+d.getClassName(),e);
        }
    }

    /**
     * Obtains the content root folders for CPS classpath.
     *
     * <p>
     * Content roots are directories in which package structures are defined and class/resources exist,
     * like 'target/classes'. Those directories are added to {@link URLClassLoader#addURL(URL)} to form
     * a classpath.
     *
     * <p>
     * Paths designated here get loaded into trusted CPS classloader, which is exposed to untrusted
     * user CPS classloader. So we need them to be isolated from the rest of the classpath of Jenkins,
     * hence {@code WEB-INF/steps}
     */
    public Iterable<URL> getContentRoots() {
        return new Iterable<URL>() {
            @Override
            public Iterator<URL> iterator() {
                return Iterators.concat(
                        additionalContentRoots.iterator(),
                        new AbstractIterator<URL>() {
                            final Iterator<PluginWrapper> base = Jenkins.getActiveInstance().getPluginManager().getPlugins().iterator();
                            @Override
                            protected URL computeNext() {
                                while (base.hasNext()) {
                                    PluginWrapper pw = base.next();
                                    try {
                                        return new URL(pw.baseResourceURL,"WEB-INF/steps");
                                    } catch (MalformedURLException e) {
                                        // impossible but let's be defensive
                                        LOGGER.log(Level.WARNING, "Failed to figure out content root for "+pw.baseResourceURL);
                                        // continue the while loop
                                    }
                                }
                                return endOfData();
                            }
                        });
            }
        };
    }

    /**
     * Adds all the content roots to the given Groovy shell.
     */
    public void addContentRootsTo(GroovyShell sh) {
        for (URL root : getContentRoots()) {
            sh.getClassLoader().addURL(root);
        }
    }

    public static GroovyCompiler get() {
        return Jenkins.getActiveInstance().getInjector().getInstance(GroovyCompiler.class);
    }

    private static final Logger LOGGER = Logger.getLogger(GroovyCompiler.class.getName());
}
