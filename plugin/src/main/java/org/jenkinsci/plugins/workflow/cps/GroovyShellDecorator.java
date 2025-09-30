package org.jenkinsci.plugins.workflow.cps;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import groovy.lang.GroovyShell;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;

/**
 * Hook to customize the behaviour of {@link GroovyShell}
 * used to run workflow scripts.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class GroovyShellDecorator implements ExtensionPoint {

    /**
     * Called with {@link ImportCustomizer} to auto-import more packages, etc.
     *
     * @param context
     *      null if {@link GroovyShell} is created just to test the parsing of the script.
     */
    public void customizeImports(@CheckForNull CpsFlowExecution context, ImportCustomizer ic) {}

    /**
     * Called with {@link CompilerConfiguration} to provide opportunity to tweak the runtime environment further.
     *
     * @param context
     *      null if {@link GroovyShell} is created just to test the parsing of the script.
     */
    public void configureCompiler(@CheckForNull CpsFlowExecution context, CompilerConfiguration cc) {}

    /**
     * Called with a configured {@link GroovyShell} to further tweak its behaviours.
     * @param context null if {@link GroovyShell} is created just to test the parsing of the script.
     */
    public void configureShell(@CheckForNull CpsFlowExecution context, GroovyShell shell) {}

    /**
     * Obtains a contextualized {@link GroovyShellDecorator} used to decorate the trusted shell.
     *
     * <p>
     * By default, this method returns null decorator that doesn't do anything.
     *
     * <p>
     * See {@code classloader.md} for details.
     */
    public GroovyShellDecorator forTrusted() {
        return NULL;
    }

    public static ExtensionList<GroovyShellDecorator> all() {
        return ExtensionList.lookup(GroovyShellDecorator.class);
    }

    /**
     * {@link GroovyShellDecorator} that doesn't do anything.
     */
    public static final GroovyShellDecorator NULL = new GroovyShellDecorator() {};
}
