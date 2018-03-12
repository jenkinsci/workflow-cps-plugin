package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;

import java.io.IOException;
import java.util.List;

import hudson.Extension;
import hudson.MarkupText;
import hudson.console.ConsoleAnnotationDescriptor;
import hudson.console.ConsoleAnnotator;
import hudson.console.ConsoleNote;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

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
                                    ScriptApprovalNote.encodeTo(Messages.SandboxContinuable_ScriptApprovalLink()));
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

    public static final class ScriptApprovalNote extends ConsoleNote {
        private int length;

        public ScriptApprovalNote(int length) {
            this.length = length;
        }

        @Override
        public ConsoleAnnotator annotate(Object context, MarkupText text, int charPos) {
            if (Jenkins.getActiveInstance().hasPermission(Jenkins.RUN_SCRIPTS)) {
                String url = "/" + ScriptApproval.get().getUrlName();

                StaplerRequest req = Stapler.getCurrentRequest();

                if (req!=null) {
                    // if we are serving HTTP request, we want to use app relative URL
                    url = req.getContextPath()+url;
                } else {
                    // otherwise presumably this is rendered for e-mails and other non-HTTP stuff
                    url = Jenkins.getInstance().getRootUrl()+url.substring(1);
                }

                text.addMarkup(charPos, charPos + length, "<a href='" + url + "'>", "</a>");
            }

            return null;
        }

        public static String encodeTo(String text) {
            try {
                return new ScriptApprovalNote(text.length()).encode()+text;
            } catch (IOException e) {
                // impossible, but don't make this a fatal problem
                LOGGER.log(Level.WARNING, "Failed to serialize "+ScriptApprovalNote.class,e);
                return text;
            }
        }

        @Extension
        @Symbol("scriptApprovalLink")
        public static class DescriptorImpl extends ConsoleAnnotationDescriptor {
            public String getDisplayName() {
                return "Script Approval Link";
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(SandboxContinuable.class.getName());
}
