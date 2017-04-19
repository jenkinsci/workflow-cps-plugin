package org.jenkinsci.plugins.workflow.cps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;

/**
 * {@link GroovyShell} with additional tweaks necessary to run {@link CpsScript}
 * @see "doc/classloader.md"
 * @see CpsGroovyShellFactory
 */
class CpsGroovyShell extends GroovyShell {

    private static final Logger LOGGER = Logger.getLogger(CpsGroovyShell.class.getName());

    /**
     * {@link CpsFlowExecution} for which this shell is created.
     *
     * Null is a valid value for just parsing the script to test-compile it without running it.
     */
    private final @CheckForNull CpsFlowExecution execution;

    /**
     * Use {@link CpsGroovyShellFactory} to instantiate it.
     */
    @SuppressFBWarnings(value="DP_CREATE_CLASSLOADER_INSIDE_DO_PRIVILEGED", justification="irrelevant")
    CpsGroovyShell(ClassLoader parent, @CheckForNull CpsFlowExecution execution, CompilerConfiguration cc) {
        super(parent,new Binding(),cc);
        this.execution = execution;
        try {
            Field loaderF = GroovyShell.class.getDeclaredField("loader");
            loaderF.setAccessible(true);
            loaderF.set(this, new CleanGroovyClassLoader(parent, cc));
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, "failed to install CleanGroovyClassLoader", x);
        }
    }

    /**
     * Disables the weird and unreliable {@link groovy.lang.GroovyClassLoader.InnerLoader}.
     * This is apparently only necessary when you are using class recompilation, which we are not.
     * @see GroovyClassLoader#getTimeStamp
     */
    private static final class CleanGroovyClassLoader extends GroovyClassLoader {

        CleanGroovyClassLoader(ClassLoader loader, CompilerConfiguration config) {
            super(loader, config);
        }

        @Override protected ClassCollector createCollector(CompilationUnit unit, SourceUnit su) {
            return new CleanClassCollector(unit, su);
        }

        private final class CleanClassCollector extends ClassCollector {

            CleanClassCollector(CompilationUnit unit, SourceUnit su) {
                super(null, unit, su);
            }

            @Override public GroovyClassLoader getDefiningClassLoader() {
                return CleanGroovyClassLoader.this;
            }

        }

    }

    public void prepareScript(Script script) {
        if (script instanceof CpsScript) {
            CpsScript cs = (CpsScript) script;
            cs.execution = execution;
            try {
                cs.$initialize();
            } catch (IOException e) {
                // TODO: write a library to let me throw this
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * When loading additional scripts, we need to remember its source text so that
     * when we come back from persistence we can load them again.
     */
    @Override
    public Script parse(GroovyCodeSource codeSource) throws CompilationFailedException {
        Script s = super.parse(codeSource);
        if (execution!=null)
            execution.loadedScripts.put(s.getClass().getName(), codeSource.getScriptText());
        prepareScript(s);
        return s;
    }

    /**
     * Used internally to reload the script back when coming back from the persisted state
     * (therefore we don't want to record this.)
     */
    /*package*/ Script reparse(String className, String text) throws CompilationFailedException {
        return super.parse(new GroovyCodeSource(text,className,DEFAULT_CODE_BASE));
    }

    /**
     * Every script we parse get caught into {@code execution.loadedScripts}, so the size
     * yields a unique enough ID.
     */
    @Override
    protected synchronized String generateScriptName() {
        if (execution!=null)
            return "Script" + (execution.loadedScripts.size()+1) + ".groovy";
        else
            return super.generateScriptName();
    }
}
