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

import hudson.AbortException;
import hudson.Extension;
import hudson.FilePath;
import hudson.model.Action;
import hudson.model.Computer;
import hudson.model.Job;
import hudson.model.Node;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.model.TopLevelItem;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.slaves.WorkspaceList;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.scm.api.SCMFileSystem;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.JOB;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinitionDescriptor;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
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
    private boolean ignoreMissingScript;
    private boolean lightweight;

    @DataBoundConstructor public CpsScmFlowDefinition(SCM scm, String scriptPath) {
        this.scm = scm;
        this.scriptPath = scriptPath;
    }

    public SCM getScm() {
        return scm;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public boolean isIgnoreMissingScript() {
        return ignoreMissingScript;
    }

    @DataBoundSetter public void setIgnoreMissingScript(boolean ignoreMissingScript) {
        this.ignoreMissingScript = ignoreMissingScript;
    }

    public boolean isLightweight() {
        return lightweight;
    }

    @DataBoundSetter public void setLightweight(boolean lightweight) {
        this.lightweight = lightweight;
    }

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
        if (isLightweight()) {
            try (SCMFileSystem fs = SCMFileSystem.of(build.getParent(), scm)) {
                if (fs != null) {
                    String script;
                    try {
                        script = fs.child(scriptPath).contentAsString();
                        listener.getLogger().println("Obtained " + scriptPath + " from " + scm.getKey());
                    } catch (java.io.FileNotFoundException e) {
                        if (isIgnoreMissingScript()) {
                            // Set script to am empty string to mimic an empty scriptFile
                            script = "";
                            listener.getLogger().println("The file " + scriptPath + " from " + scm.getKey() + " was not found, ignoring");
                        } else {
                            throw e;
                        }
                    }
                    return new CpsFlowExecution(script, true, owner);
                } else {
                    listener.getLogger().println("Lightweight checkout support not available, falling back to full checkout.");
                }
            }
        }
        listener.getLogger().println("Checking out " + scm.getKey() + " to read " + scriptPath);
        FilePath dir;
        Node node = Jenkins.getActiveInstance();
        if (build.getParent() instanceof TopLevelItem) {
            FilePath baseWorkspace = node.getWorkspaceFor((TopLevelItem) build.getParent());
            if (baseWorkspace == null) {
                throw new IOException(node.getDisplayName() + " may be offline");
            }
            dir = getFilePathWithSuffix(baseWorkspace);
        } else { // should not happen, but just in case:
            dir = new FilePath(owner.getRootDir());
        }
        String script;
        Computer computer = node.toComputer();
        if (computer == null) {
            throw new IOException(node.getDisplayName() + " may be offline");
        }
        SCMStep delegate = new GenericSCMStep(scm);
        delegate.setPoll(true);
        delegate.setChangelog(true);
        try (WorkspaceList.Lease lease = computer.getWorkspaceList().acquire(dir)) {
            delegate.checkout(build, dir, listener, node.createLauncher(listener));
            FilePath scriptFile = dir.child(scriptPath);
            if (!scriptFile.absolutize().getRemote().replace('\\', '/').startsWith(dir.absolutize().getRemote().replace('\\', '/') + '/')) { // TODO JENKINS-26838
                throw new IOException(scriptFile + " is not inside " + dir);
            }
            if (!scriptFile.exists()) {
                if (isIgnoreMissingScript()) {
                    // Set script to am empty string to mimic an empty scriptFile
                    script = "";
                    listener.getLogger().println("The file " + scriptPath + " from " + scm.getKey() + " was not found, ignoring");
                } else {
                    throw new AbortException(scriptFile + " not found");
                }
            } else {
                script = scriptFile.readToString();
            }

        }
        CpsFlowExecution exec = new CpsFlowExecution(script, true, owner);
        exec.flowStartNodeActions.add(new WorkspaceActionImpl(dir, null));
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
