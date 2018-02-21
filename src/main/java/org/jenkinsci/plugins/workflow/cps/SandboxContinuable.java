package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;

import java.io.IOException;
import java.util.List;

import hudson.console.HyperlinkNote;
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;

import java.util.concurrent.Callable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;

/**
 * {@link Continuable} that executes code inside sandbox execution.
 *
 * @author Kohsuke Kawaguchi
 */
class SandboxContinuable extends Continuable {
    private final CpsThread thread;

    SandboxContinuable(Continuable src, CpsThread thread) {
        super(src);
        this.thread = thread;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public Outcome run0(final Outcome cn, final List<Class> categories) {
        try {
            CpsFlowExecution e = thread.group.getExecution();
            return GroovySandbox.runInSandbox(new Callable<Outcome>() {
                @Override
                public Outcome call() {
                    Outcome outcome = SandboxContinuable.super.run0(cn, categories);
                    RejectedAccessException x = findRejectedAccessException(outcome.getAbnormal());
                    if (x != null) {
                        ScriptApproval.get().accessRejected(x, ApprovalContext.create());
                        try {
                            e.getOwner().getListener().getLogger().println(x.getMessage() + ". " +
                                    HyperlinkNote.encodeTo("/" + ScriptApproval.get().getUrlName(),
                                    Messages.SandboxContinuable_ScriptApprovalLink()));
                        } catch (IOException ex) {
                            LOGGER.log(Level.WARNING, null, ex);
                        }
                    }
                    return outcome;
                }
            }, new GroovyClassLoaderWhitelist(CpsWhitelist.get(),
                    e.getTrustedShell().getClassLoader(),
                    e.getShell().getClassLoader()));
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new AssertionError(e);    // Callable doesn't throw anything
        }
    }

    private static @CheckForNull RejectedAccessException findRejectedAccessException(@CheckForNull Throwable t) {
        if (t == null) {
            return null;
        } else if (t instanceof RejectedAccessException) {
            return (RejectedAccessException) t;
        } else {
            return findRejectedAccessException(t.getCause());
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SandboxContinuable.class.getName());
}
