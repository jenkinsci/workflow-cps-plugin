package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;
import groovy.lang.GroovyShell;
import java.util.List;
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;

import java.util.concurrent.Callable;
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
            if (e == null) {
                throw new IllegalStateException("no loaded execution");
            }
            GroovyShell shell = e.getShell();
            if (shell == null) {
                throw new IllegalStateException("no loaded shell in " + e);
            }
            GroovyShell trustedShell = e.getTrustedShell();
            if (trustedShell == null) {
                throw new IllegalStateException("no loaded trustedShell in " + e);
            }
            return GroovySandbox.runInSandbox(() -> {
                    Outcome outcome = SandboxContinuable.super.run0(cn, categories);
                    RejectedAccessException x = findRejectedAccessException(outcome.getAbnormal());
                    if (x != null) {
                        ScriptApproval.get().accessRejected(x, ApprovalContext.create());
                    }
                    return outcome;
            }, new GroovyClassLoaderWhitelist(CpsWhitelist.get(),
                    trustedShell.getClassLoader(),
                    shell.getClassLoader()));
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

}
