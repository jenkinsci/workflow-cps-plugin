/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.cps;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.Functions;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.slaves.WorkspaceList;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Collection;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMFileSystem;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.JOB;

import org.jenkinsci.plugins.workflow.flow.DurabilityHintProvider;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinitionDescriptor;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.GlobalDefaultFlowDurabilityLevel;
import org.jenkinsci.plugins.workflow.steps.scm.GenericSCMStep;
import org.jenkinsci.plugins.workflow.steps.scm.SCMStep;
import org.jenkinsci.plugins.workflow.support.actions.WorkspaceActionImpl;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

@PersistIn(JOB)
public class CpsScmFlowDefinition extends FlowDefinition {

    private final SCM scm;
    private final String scriptPath;
    private boolean lightweight;

    @DataBoundConstructor public CpsScmFlowDefinition(SCM scm, String scriptPath) {
        this.scm = scm;
        this.scriptPath = scriptPath.trim();
    }

    public SCM getScm() {
        return scm;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public boolean isLightweight() {
        return lightweight;
    }

    @DataBoundSetter public void setLightweight(boolean lightweight) {
        this.lightweight = lightweight;
    }

    @SuppressFBWarnings(
            value = {"NP_LOAD_OF_KNOWN_NULL_VALUE", "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE",
                    "RCN_REDUNDANT_NULLCHECK_OF_NULL_VALUE", "RCN_REDUNDANT_NULLCHECK_WOULD_HAVE_BEEN_A_NPE"},
            justification = "false positives for try-resource in java 11"
    )
    @Override public CpsFlowExecution create(FlowExecutionOwner owner, TaskListener listener, List<? extends Action> actions) throws Exception {
        for (Action a : actions) {
            if (a instanceof CpsFlowFactoryAction2) {
                return ((CpsFlowFactoryAction2) a).create(this, owner, actions);
            }
        }
        Queue.Executable _build = owner.getExecutable();
        if (!(_build instanceof Run)) {
            throw new IOException("can only check out SCM into a Run");
        }
        Run<?,?> build = (Run<?,?>) _build;
        String expandedScriptPath = build.getEnvironment(listener).expand(scriptPath);
        if (isLightweight()) {
            try (SCMFileSystem fs = SCMFileSystem.of(build, scm)) {
                if (fs != null) {
                    try {
                        String script = fs.child(expandedScriptPath).contentAsString();
                        listener.getLogger().println("Obtained " + expandedScriptPath + " from " + scm.getKey());
                        Queue.Executable exec = owner.getExecutable();
                        FlowDurabilityHint hint = (exec instanceof Item) ? DurabilityHintProvider.suggestedFor((Item) exec) : GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint();
                        return new CpsFlowExecution(script, true, owner, hint);
                    } catch (FileNotFoundException e) {
                        throw new AbortException("Unable to find " + expandedScriptPath + " from " + scm.getKey());
                    }
                } else {
                    listener.getLogger().println("Lightweight checkout support not available, falling back to full checkout.");
                }
            }
        }
        FilePath dir;
        Node node = Jenkins.get();
        if (build.getParent() instanceof TopLevelItem) {
            FilePath baseWorkspace = node.getWorkspaceFor((TopLevelItem) build.getParent());
            if (baseWorkspace == null) {
                throw new IOException(node.getDisplayName() + " may be offline");
            }
            dir = getFilePathWithSuffix(baseWorkspace);
        } else { // should not happen, but just in case:
            dir = new FilePath(owner.getRootDir());
        }
        listener.getLogger().println("Checking out " + scm.getKey() + " into " + dir + " to read " + expandedScriptPath);
        String script = null;
        Computer computer = node.toComputer();
        if (computer == null) {
            throw new IOException(node.getDisplayName() + " may be offline");
        }
        SCMStep delegate = new GenericSCMStep(scm);
        delegate.setPoll(true);
        delegate.setChangelog(true);
        FilePath acquiredDir;
        try (WorkspaceList.Lease lease = computer.getWorkspaceList().acquire(dir)) {
            for (int retryCount = Jenkins.get().getScmCheckoutRetryCount(); retryCount >= 0; retryCount--) {
                try {
                    delegate.checkout(build, dir, listener, node.createLauncher(listener));
                    break;
                } catch (AbortException e) {
                    // abort exception might have a null message.
                    // If so, just skip echoing it.
                    if (e.getMessage() != null) {
                        listener.error(e.getMessage());
                    }
                } catch (InterruptedIOException e) {
                    throw e;
                } catch (Exception e) {
                    // checkout error not yet reported
                    Functions.printStackTrace(e, listener.error("Checkout failed"));
                }

                if (retryCount == 0)   // all attempts failed
                    throw new AbortException("Maximum checkout retry attempts reached, aborting");

                listener.getLogger().println("Retrying after 10 seconds");
                Thread.sleep(10000);
            }

            FilePath scriptFile = dir.child(expandedScriptPath);
            if (!scriptFile.absolutize().getRemote().replace('\\', '/').startsWith(dir.absolutize().getRemote().replace('\\', '/') + '/')) { // TODO JENKINS-26838
                throw new IOException(scriptFile + " is not inside " + dir);
            }
            if (!scriptFile.exists()) {
                throw new AbortException(scriptFile + " not found");
            }
            script = scriptFile.readToString();
            acquiredDir = lease.path;
        }
        Queue.Executable queueExec = owner.getExecutable();
        FlowDurabilityHint hint = (queueExec instanceof Run) ? DurabilityHintProvider.suggestedFor(((Run)queueExec).getParent()) : GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint();
        CpsFlowExecution exec = new CpsFlowExecution(script, true, owner, hint);
        exec.flowStartNodeActions.add(new WorkspaceActionImpl(acquiredDir, null));
        return exec;
    }

    private FilePath getFilePathWithSuffix(FilePath baseWorkspace) {
        return baseWorkspace.withSuffix(getFilePathSuffix() + "script");
    }

    private String getFilePathSuffix() {
        return System.getProperty(WorkspaceList.class.getName(), "@");
    }

    @Extension public static class DescriptorImpl extends FlowDefinitionDescriptor {

        @Override public String getDisplayName() {
            return "Pipeline script from SCM";
        }

        public Collection<? extends SCMDescriptor<?>> getApplicableDescriptors() {
            StaplerRequest req = Stapler.getCurrentRequest();
            Job<?,?> job = req != null ? req.findAncestorObject(Job.class) : null;
            return SCM._for(job);
        }

        // TODO doCheckLightweight impossible to write even though we have SCMFileSystem.supports(SCM), because form validation cannot pass the SCM object

    }

}
