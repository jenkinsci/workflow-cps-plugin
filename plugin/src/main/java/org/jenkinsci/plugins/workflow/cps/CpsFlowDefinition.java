/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.FormValidation;
import hudson.util.StreamTaskListener;
import net.sf.json.JSONObject;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.cps.view.ThemeUtil;
import org.jenkinsci.plugins.workflow.flow.DurabilityHintProvider;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDefinitionDescriptor;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.GlobalDefaultFlowDurabilityLevel;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import net.sf.json.JSON;
import net.sf.json.JSONArray;
import org.codehaus.groovy.control.CompilationFailedException;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.JOB;

import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * @author Kohsuke Kawaguchi
 */
@PersistIn(JOB)
public class CpsFlowDefinition extends FlowDefinition {
    private final String script;
    private final boolean sandbox;

    /**
     * @deprecated use {@link #CpsFlowDefinition(String, boolean)} instead
     */
    @Deprecated
    public CpsFlowDefinition(String script) {
        this(script, false);
    }

    @DataBoundConstructor
    public CpsFlowDefinition(String script, boolean sandbox) {
        StaplerRequest req = Stapler.getCurrentRequest();
        this.script = sandbox ? script : ScriptApproval.get().configuring(script, GroovyLanguage.get(),
                ApprovalContext.create().withCurrentUser().withItemAsKey(req != null ? req.findAncestorObject(Item.class) : null), req == null);
        this.sandbox = sandbox;
    }

    private Object readResolve() {
        if (!sandbox) {
            ScriptApproval.get().configuring(script, GroovyLanguage.get(), ApprovalContext.create(), true);
        }
        return this;
    }

    public String getScript() {
        return script;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    // Used only from Groovy tests.
    public CpsFlowExecution create(FlowExecutionOwner handle, Action... actions) throws IOException {
        return create(handle, StreamTaskListener.fromStderr(), List.of(actions));
    }

    @Override
    @SuppressWarnings("deprecation")
    public CpsFlowExecution create(FlowExecutionOwner owner, TaskListener listener, List<? extends Action> actions) throws IOException {
        for (Action a : actions) {
            if (a instanceof CpsFlowFactoryAction) {
                CpsFlowFactoryAction fa = (CpsFlowFactoryAction) a;
                return fa.create(this,owner,actions);
            } else if (a instanceof CpsFlowFactoryAction2) {
                return ((CpsFlowFactoryAction2) a).create(this, owner, actions);
            }
        }
        Queue.Executable exec = owner.getExecutable();
        FlowDurabilityHint hint = (exec instanceof Run) ? DurabilityHintProvider.suggestedFor(((Run)exec).getParent()) : GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint();
        return new CpsFlowExecution(sandbox ? script : ScriptApproval.get().using(script, GroovyLanguage.get()), sandbox, owner, hint);
    }

    @Extension
    public static class DescriptorImpl extends FlowDefinitionDescriptor {

        /* In order to fix SECURITY-2450 without causing significant UX regressions, we decided to continue to
         * automatically approve scripts on save if the script was modified by an administrator. To make this possible,
         * we added a new hidden input field to the config.jelly to track the pre-save version of the script. Since
         * CpsFlowDefinition calls ScriptApproval.configuring in its @DataBoundConstructor, the normal way to handle
         * things would be to add an oldScript parameter to the constructor and perform the relevant logic there.
         *
         * However, that would have compatibility implications for tools like JobDSL, since @DataBoundConstructor
         * parameters are required. We cannot use a @DataBoundSetter with a corresponding field and getter to trivially
         * make oldScript optional, because we would need to call ScriptApproval.configuring after all
         * @DataBoundSetters have been invoked (rather than in the @DataBoundConstructor), which is why we use Descriptor.newInstance.
         */
        @Override
        public FlowDefinition newInstance(@NonNull StaplerRequest req, @NonNull JSONObject formData) throws FormException {
            CpsFlowDefinition cpsFlowDefinition = (CpsFlowDefinition) super.newInstance(req, formData);
            if (!cpsFlowDefinition.sandbox && formData.get("oldScript") != null) {
                String oldScript = formData.getString("oldScript");
                boolean approveIfAdmin = !StringUtils.equals(oldScript, cpsFlowDefinition.script);
                if (approveIfAdmin) {
                    ScriptApproval.get().configuring(cpsFlowDefinition.script, GroovyLanguage.get(),
                            ApprovalContext.create().withCurrentUser().withItemAsKey(req.findAncestorObject(Item.class)), true);
                }
            }
            return cpsFlowDefinition;
        }

        @Override
        public String getDisplayName() {
            return "Pipeline script";
        }

        @RequirePOST
        public FormValidation doCheckScript(@QueryParameter String value, @QueryParameter String oldScript,
                                            @QueryParameter boolean sandbox) {
            return sandbox ? FormValidation.ok() :
                    ScriptApproval.get().checking(value, GroovyLanguage.get(), !StringUtils.equals(oldScript, value));
        }

        @RequirePOST
        public JSON doCheckScriptCompile(@AncestorInPath Item job, @QueryParameter String value) {
            if (!job.hasPermission(Job.CONFIGURE)) {
                return CpsFlowDefinitionValidator.CheckStatus.SUCCESS.asJSON();
            }
            try {
                CpsGroovyShell trusted = new CpsGroovyShellFactory(null).forTrusted().build();
                new CpsGroovyShellFactory(null).withParent(trusted).withSandbox(true).build().getClassLoader().parseClass(value);
            } catch (CompilationFailedException x) {
                return JSONArray.fromObject(CpsFlowDefinitionValidator.toCheckStatus(x).toArray());
            }
            return CpsFlowDefinitionValidator.CheckStatus.SUCCESS.asJSON();
            // Approval requirements are managed by regular stapler form validation (via doCheckScript)
        }

    }

    /** @see ReplayAction#getTheme */
    @Restricted(DoNotUse.class)
    /* accessible to Jelly */ public String getTheme() {
        return ThemeUtil.getTheme();
    }
}
