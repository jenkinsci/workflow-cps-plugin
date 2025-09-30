package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.CpsTransformer;
import com.cloudbees.groovy.cps.NonCPS;
import com.cloudbees.groovy.cps.SandboxCpsTransformer;
import com.cloudbees.groovy.cps.TransformerConfiguration;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import groovy.lang.GroovyShell;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.Jenkins;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox;

/**
 * Instantiates {@link CpsGroovyShell}.
 *
 * @author Kohsuke Kawaguchi
 */
class CpsGroovyShellFactory {
    private final @CheckForNull CpsFlowExecution execution;
    private boolean sandbox;
    private List<GroovyShellDecorator> decorators;
    private ClassLoader parent;

    /**
     * @param execution
     *      Instantiated {@link CpsGroovyShell} will be used to load scripts for this execution.
     */
    public CpsGroovyShellFactory(@Nullable CpsFlowExecution execution) {
        this.execution = execution;
        this.sandbox = execution != null && execution.isSandbox();
        this.decorators = GroovyShellDecorator.all();
    }

    private CpsGroovyShellFactory(
            CpsFlowExecution execution, boolean sandbox, ClassLoader parent, List<GroovyShellDecorator> decorators) {
        this.execution = execution;
        this.sandbox = sandbox;
        this.parent = parent;
        this.decorators = decorators;
    }

    /**
     * Derives a new factory for creating trusted {@link CpsGroovyShell}
     */
    public CpsGroovyShellFactory forTrusted() {
        List<GroovyShellDecorator> inner = new ArrayList<>();
        for (GroovyShellDecorator d : decorators) {
            inner.add(d.forTrusted());
        }
        return new CpsGroovyShellFactory(execution, false, parent, inner);
    }

    /**
     * Enables/disables the use of script-security on scripts loaded into {@link CpsGroovyShell}.
     * This method can be called to override the setting in {@link CpsFlowExecution}.
     */
    public CpsGroovyShellFactory withSandbox(boolean b) {
        this.sandbox = b;
        return this;
    }

    public CpsGroovyShellFactory withParent(GroovyShell parent) {
        return withParent(parent.getClassLoader());
    }

    /**
     * Sets the parent classloader for the {@link CpsGroovyShell}.
     */
    public CpsGroovyShellFactory withParent(ClassLoader parent) {
        this.parent = parent;
        return this;
    }

    private CpsTransformer makeCpsTransformer() {
        CpsTransformer t = sandbox ? new SandboxCpsTransformer() : new CpsTransformer();
        t.setConfiguration(new TransformerConfiguration()
                .withClosureType(CpsClosure2.class)
                .withSafepoint(Safepoint.class, "safepoint"));
        return t;
    }

    private CompilerConfiguration makeConfig() {
        CompilerConfiguration cc =
                sandbox ? GroovySandbox.createBaseCompilerConfiguration() : new CompilerConfiguration();

        cc.addCompilationCustomizers(makeImportCustomizer());
        cc.addCompilationCustomizers(makeCpsTransformer());

        cc.setScriptBaseClass(CpsScript.class.getName());

        for (GroovyShellDecorator d : decorators) {
            d.configureCompiler(execution, cc);
        }

        return cc;
    }

    private ImportCustomizer makeImportCustomizer() {
        ImportCustomizer ic = new ImportCustomizer();
        ic.addStarImports(NonCPS.class.getPackage().getName());
        ic.addStarImports("hudson.model", "jenkins.model");

        for (GroovyShellDecorator d : decorators) {
            d.customizeImports(execution, ic);
        }
        return ic;
    }

    private ClassLoader makeClassLoader() {
        ClassLoader cl = Jenkins.get().getPluginManager().uberClassLoader;
        return new GroovySourceFileAllowlist.ClassLoaderImpl(execution, GroovySandbox.createSecureClassLoader(cl));
    }

    public CpsGroovyShell build() {
        ClassLoader parent = this.parent;
        if (parent == null) parent = makeClassLoader();

        CpsGroovyShell shell = new CpsGroovyShell(parent, execution, makeConfig());

        for (GroovyShellDecorator d : decorators) {
            d.configureShell(execution, shell);
        }

        return shell;
    }
}
