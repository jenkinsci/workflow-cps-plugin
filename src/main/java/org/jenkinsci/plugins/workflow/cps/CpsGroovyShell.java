package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.Binding;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilerConfiguration;

import javax.annotation.CheckForNull;
import java.io.IOException;

/**
 * {@link GroovyShell} with additional tweaks necessary to run {@link CpsScript}
 *
 * @author Kohsuke Kawaguchi
 * @see "doc/clasloader.md"
 * @see CpsGroovyShellFactory
 */
class CpsGroovyShell extends GroovyShell {
    /**
     * {@link CpsFlowExecution} for which this shell is created.
     *
     * Null is a valid value for just parsing the script to test-compile it without running it.
     */
    private final @CheckForNull CpsFlowExecution execution;

    /**
     * Use {@link CpsGroovyShellFactory} to instantiate it.
     */
    CpsGroovyShell(ClassLoader parent, @CheckForNull CpsFlowExecution execution, CompilerConfiguration cc) {
        super(parent,new Binding(),cc);
        this.execution = execution;
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
