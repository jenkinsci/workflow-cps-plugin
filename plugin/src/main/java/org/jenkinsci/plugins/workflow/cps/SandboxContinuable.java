package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;
import groovy.lang.GroovyShell;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox;

/**
 * {@link Continuable} that executes code inside sandbox execution.
 */
class SandboxContinuable extends Continuable {
    private final CpsThread thread;

    SandboxContinuable(Continuable src, CpsThread thread) {
        super(src);
        this.thread = thread;
    }

    @SuppressWarnings("rawtypes")
    public Outcome run0(final Outcome cn) {
        CpsFlowExecution e = thread.group.getExecution();
        if (e == null) {
            throw new IllegalStateException("JENKINS-50407: no loaded execution");
        }
        GroovyShell shell = e.getShell();
        if (shell == null) {
            throw new IllegalStateException("JENKINS-50407: no loaded shell in " + e);
        }
        GroovyShell trustedShell = e.getTrustedShell();
        if (trustedShell == null) {
            throw new IllegalStateException("JENKINS-50407: no loaded trustedShell in " + e);
        }
        GroovySandbox sandbox = new GroovySandbox();
        try {
            sandbox.withTaskListener(e.getOwner().getListener());
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, null, x);
        }
        sandbox.withWhitelist(new GroovyClassLoaderWhitelist(
                CpsWhitelist.get(), trustedShell.getClassLoader(), shell.getClassLoader()));
        try (GroovySandbox.Scope scope = sandbox.enter()) {
            return SandboxContinuable.super.run0(cn);
        }
    }

    @SuppressWarnings("rawtypes")
    @Deprecated
    /** @deprecated Only here for serial compatibility. */
    private static final class ScriptApprovalNote extends ConsoleNote {
        private int length;

        @Override
        public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
            return null;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SandboxContinuable.class.getName());
}
