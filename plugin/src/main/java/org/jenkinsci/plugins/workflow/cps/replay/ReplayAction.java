/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps.replay;

import com.cloudbees.diff.Diff;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Action;
import hudson.model.Cause;
import hudson.model.CauseAction;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.PasswordParameterValue;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.queue.QueueTaskFuture;
import hudson.security.Permission;
import hudson.security.PermissionScope;
import hudson.util.FormValidation;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.servlet.ServletException;

import hudson.util.HttpResponses;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import jenkins.model.TransientActionFactory;
import jenkins.scm.api.SCMRevisionAction;
import net.sf.json.JSON;
import net.sf.json.JSONObject;
import org.acegisecurity.AccessDeniedException;
import org.jenkinsci.plugins.scriptsecurity.scripts.ApprovalContext;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.UnapprovedUsageException;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;
import org.kohsuke.stapler.interceptor.RequirePOST;

import static javax.servlet.http.HttpServletResponse.SC_CONFLICT;

/**
 * Attached to a {@link Run} when it could be replayed with script edits.
 */
@SuppressWarnings("rawtypes") // on Run
public class ReplayAction implements Action {

    private static final Logger LOGGER = Logger.getLogger(ReplayAction.class.getName());

    private final Run run;

    private ReplayAction(Run run) {
        this.run = run;
    }

    @Override public String getDisplayName() {
        return isEnabled() ? Messages.ReplayAction_displayName() : Messages.ReplayAction_rebuild_displayName();
    }

    @Override public String getIconFileName() {
        return isEnabled() || isRebuildEnabled() ? "symbol-arrow-redo-outline plugin-ionicons-api" : null;
    }

    @Override public String getUrlName() {
        return isEnabled() || isRebuildEnabled() ? "replay" : null;
    }

    /** Poke for an execution without blocking - may be null if run is very fresh or has not lazy-loaded yet. */
    private @CheckForNull CpsFlowExecution getExecutionLazy() {
        FlowExecutionOwner owner = ((FlowExecutionOwner.Executable) run).asFlowExecutionOwner();
        if (owner == null) {
            return null;
        }
        FlowExecution exec = owner.getOrNull();
        return exec instanceof CpsFlowExecution ? (CpsFlowExecution) exec : null;
    }

    /** Fetches execution, blocking if needed while we wait for some of the loading process. */
    @Restricted(NoExternalUse.class)
    public @CheckForNull CpsFlowExecution getExecutionBlocking() {
        FlowExecutionOwner owner = ((FlowExecutionOwner.Executable) run).asFlowExecutionOwner();
        if (owner == null) {
            return null;
        }
        try {
            FlowExecution exec = owner.get();
            return exec instanceof CpsFlowExecution ? (CpsFlowExecution) exec : null;
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Error fetching execution for replay", ioe);
        }
        return null;
    }

    /* accessible to Jelly */ public boolean isRebuildEnabled() {
        if (!run.hasPermission(Item.BUILD)) {
            return false;
        }
        if (!run.getParent().isBuildable()) {
            return false;
        }

        return true;
    }

    /* accessible to Jelly */ public boolean isEnabled() {
        if (!run.hasPermission(REPLAY)) {
            return false;
        }

        if (!run.getParent().isBuildable()) {
            return false;
        }

        CpsFlowExecution exec = getExecutionLazy();
        if (exec != null) {
            return exec.isSandbox() || Jenkins.get().hasPermission(Jenkins.ADMINISTER); // We have to check for ADMINISTER because un-sandboxed code can execute arbitrary on-controller code
        } else {
            // If the execution hasn't been lazy-loaded then we will wait to do deeper checks until someone tries to lazy load
            // OR until isReplayableSandboxTest is invoked b/c they actually try to replay the build
            return true;
        }
    }

    private boolean isSandboxed() {
        CpsFlowExecution exec = getExecutionLazy();
        if (exec != null) {
            return exec.isSandbox();
        }
        return false;
    }

    /** Runs the extra tests for replayability beyond {@link #isEnabled()} that require a blocking load of the execution. */
    /* accessible to Jelly */ public boolean isReplayableSandboxTest() {
        CpsFlowExecution exec = getExecutionBlocking();
        if (exec != null) {
            if (!exec.isSandbox()) {
                // We have to check for ADMINISTER because un-sandboxed code can execute arbitrary on-controller code
                return Jenkins.get().hasPermission(Jenkins.ADMINISTER);
            }
            return true;
        }
        return false;
    }

    /** @see CpsFlowExecution#getScript */
    /* accessible to Jelly */ public String getOriginalScript() {
        CpsFlowExecution execution = getExecutionBlocking();
        return execution != null ? execution.getScript() : "???";
    }

    /** @see CpsFlowExecution#getLoadedScripts */
    /* accessible to Jelly */ public Map<String,String> getOriginalLoadedScripts() {
        CpsFlowExecution execution = getExecutionBlocking();
        if (execution == null) { // ?
            return Collections.<String,String>emptyMap();
        }
        Map<String,String> scripts = new TreeMap<>();
        for (OriginalLoadedScripts replayer : ExtensionList.lookup(OriginalLoadedScripts.class)) {
            scripts.putAll(replayer.loadScripts(execution));
        }
        return scripts;
    }

    /* accessible to Jelly */ public Run getOwner() {
        return run;
    }

    @Restricted(DoNotUse.class)
    @RequirePOST
    public void doRun(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        if (!isEnabled() || !(isReplayableSandboxTest())) {
            throw new AccessDeniedException("not allowed to replay"); // AccessDeniedException2 requires us to look up the specific Permission
        }
        JSONObject form = req.getSubmittedForm();
        // Copy originalLoadedScripts, replacing values with those from the form wherever defined.
        Map<String,String> replacementLoadedScripts = new HashMap<>();
        for (Map.Entry<String,String> entry : getOriginalLoadedScripts().entrySet()) {
            // optString since you might be replaying a running build, which might have loaded a script after the page load but before submission.
            replacementLoadedScripts.put(entry.getKey(), form.optString(entry.getKey().replace('.', '_'), entry.getValue()));
        }
        if (run(form.getString("mainScript"), replacementLoadedScripts) == null) {
            throw HttpResponses.error(SC_CONFLICT, new IOException(run.getParent().getFullName() + " is not buildable"));

        }
        rsp.sendRedirect("../.."); // back to WorkflowJob; new build might not start instantly so cannot redirect to it
    }

    @Restricted(DoNotUse.class)
    @RequirePOST
    public void doRebuild(StaplerRequest req, StaplerResponse rsp) throws ServletException, IOException {
        if (!isRebuildEnabled()) {
            throw new AccessDeniedException("not allowed to replay"); // AccessDeniedException2 requires us to look up the specific Permission
        }
        if (run(getOriginalScript(), getOriginalLoadedScripts()) == null) {
            throw HttpResponses.error(SC_CONFLICT, new IOException(run.getParent().getFullName() + " is not buildable"));

        }
        rsp.sendRedirect("../.."); // back to WorkflowJob; new build might not start instantly so cannot redirect to it
    }

    private static final Iterable<Class<? extends Action>> COPIED_ACTIONS = List.of(
        ParametersAction.class,
        SCMRevisionAction.class
    );

    /**
     * For whitebox testing.
     * @param replacementMainScript main script; replacement for {@link #getOriginalScript}
     * @param replacementLoadedScripts auxiliary scripts, keyed by class name; replacement for {@link #getOriginalLoadedScripts}
     * @return a way to wait for the replayed build to complete
     */
    public @CheckForNull QueueTaskFuture/*<Run>*/ run(@NonNull String replacementMainScript, @NonNull Map<String,String> replacementLoadedScripts) {
        Queue.Item item = run2(replacementMainScript, replacementLoadedScripts);
        return item == null ? null : item.getFuture();
    }

    /**
     * For use in projects that want initiate a replay via the Java API.
     *
     * @param replacementMainScript main script; replacement for {@link #getOriginalScript}
     * @param replacementLoadedScripts auxiliary scripts, keyed by class name; replacement for {@link #getOriginalLoadedScripts}
     * @return build queue item
     */
    public @CheckForNull Queue.Item run2(@NonNull String replacementMainScript, @NonNull Map<String,String> replacementLoadedScripts) {
        List<Action> actions = new ArrayList<>();
        CpsFlowExecution execution = getExecutionBlocking();
        if (execution == null) {
            return null;
        }

        if (!execution.isSandbox()) {
            ScriptApproval.get().configuring(replacementMainScript,GroovyLanguage.get(), ApprovalContext.create(), true);
            try {
                ScriptApproval.get().using(replacementMainScript, GroovyLanguage.get());
            } catch (UnapprovedUsageException e) {
                throw new Failure("The script is not approved.");
            }
        }

        actions.add(new ReplayFlowFactoryAction(replacementMainScript, replacementLoadedScripts, execution.isSandbox()));
        actions.add(new CauseAction(new Cause.UserIdCause(), new ReplayCause(run)));

        if (hasPasswordParameter(this.run)) {
            throw new Failure("Replay is not allowed when password parameters are used.");
        }

        for (Class<? extends Action> c : COPIED_ACTIONS) {
            actions.addAll(run.getActions(c));
        }
        return ParameterizedJobMixIn.scheduleBuild2(run.getParent(), 0, actions.toArray(new Action[actions.size()]));
    }

    private boolean hasPasswordParameter(Run run) {
        ParametersAction pa = run.getAction(ParametersAction.class);
        return pa != null && pa.getParameters().stream().anyMatch(PasswordParameterValue.class::isInstance);
    }

    /**
     * Finds a set of Groovy class names which are eligible for replacement.
     * @param execution the associated execution
     * @return Groovy class names expected to be produced, like {@code Script1}
     */
    public static @NonNull Set<String> replacementsIn(@NonNull CpsFlowExecution execution) throws IOException {
        Queue.Executable executable = execution.getOwner().getExecutable();
        if (executable instanceof Run) {
            ReplayFlowFactoryAction action = ((Run) executable).getAction(ReplayFlowFactoryAction.class);
            if (action != null) {
                return action.replaceableScripts();
            } else {
                LOGGER.log(Level.FINE, "{0} was not a replay", executable);
            }
        } else {
            LOGGER.log(Level.FINE, "{0} was not a run at all", executable);
        }
        return Collections.emptySet();
    }

    /**
     * Replaces some loaded script text with something else.
     * May be done only once per class.
     * @param execution the associated execution
     * @param clazz an entry possibly in {@link #replacementsIn}
     * @return the replacement text, or null if no replacement was available for some reason
     */
    public static @CheckForNull String replace(@NonNull CpsFlowExecution execution, @NonNull String clazz) throws IOException {
        Queue.Executable executable = execution.getOwner().getExecutable();
        if (executable instanceof Run) {
            ReplayFlowFactoryAction action = ((Run) executable).getAction(ReplayFlowFactoryAction.class);
            if (action != null) {
                return action.replace(clazz);
            } else {
                LOGGER.log(Level.FINE, "{0} was not a replay", executable);
            }
        } else {
            LOGGER.log(Level.FINE, "{0} was not a run at all", executable);
        }
        return null;
    }

    public String getDiff() {
        Run<?,?> original = run;
        ReplayCause cause;
        while ((cause = original.getCause(ReplayCause.class)) != null) {
            Run<?,?> earlier = cause.getOriginal();
            if (earlier == null) {
                // Deleted? Oh well.
                break;
            }
            original = earlier;
        }
        ReplayAction originalAction = original.getAction(ReplayAction.class);
        if (originalAction == null) {
            return "???";
        }
        try {
            StringBuilder diff = new StringBuilder(diff(/* TODO JENKINS-31838 */"Jenkinsfile", originalAction.getOriginalScript(), getOriginalScript()));
            Map<String,String> originalLoadedScripts = originalAction.getOriginalLoadedScripts();
            for (Map.Entry<String,String> entry : getOriginalLoadedScripts().entrySet()) {
                String script = entry.getKey();
                String originalScript = originalLoadedScripts.get(script);
                if (originalScript != null) {
                    diff.append(diff(script, originalScript, entry.getValue()));
                }
            }
            return diff.toString();
        } catch (IOException x) {
            return Functions.printThrowable(x);
        }
    }
    private static String diff(String script, String oldText, String nueText) throws IOException {
        Diff hunks = Diff.diff(new StringReader(oldText), new StringReader(nueText), false);
        // TODO rather than old vs. new could use (e.g.) build-10 vs. build-13
        return hunks.isEmpty() ? "" : hunks.toUnifiedDiff("old/" + script, "new/" + script, new StringReader(oldText), new StringReader(nueText), 3);
    }

    /**
     * Loaded scripts do not need to be approved.
     */
    @RequirePOST
    public FormValidation doCheckLoadedScript() {
        return FormValidation.ok();
    }

    /**
     * Form validation for the main script
     * Jelly only
     * @param value the script being checked
     * @return a message indicating that the script needs to be approved; nothing if the script is empty;
     *          a corresponding message if the script is approved
     */
    @RequirePOST
    public FormValidation doCheckScript(@QueryParameter String value) {
        return Jenkins.get().getDescriptorByType(CpsFlowDefinition.DescriptorImpl.class).doCheckScript(value, "", isSandboxed());
    }

    @RequirePOST
    public JSON doCheckScriptCompile(@AncestorInPath Item job, @QueryParameter String value) {
        return Jenkins.get().getDescriptorByType(CpsFlowDefinition.DescriptorImpl.class).doCheckScriptCompile(job, value);
    }

    public static final Permission REPLAY = new Permission(Run.PERMISSIONS, "Replay", Messages._Replay_permission_description(), Item.CONFIGURE, PermissionScope.RUN);

    @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_NO_SIDE_EFFECT", justification="getEnabled return value discarded")
    @Initializer(after=InitMilestone.PLUGINS_STARTED, before=InitMilestone.EXTENSIONS_AUGMENTED)
    public static void ensurePermissionRegistered() {
        REPLAY.getEnabled();
    }

    @Extension public static class Factory extends TransientActionFactory<Run> {

        @Override public Class<Run> type() {
            return Run.class;
        }

        @Override public Collection<? extends Action> createFor(Run run) {
            return run instanceof FlowExecutionOwner.Executable && run.getParent() instanceof ParameterizedJobMixIn.ParameterizedJob ? Collections.<Action>singleton(new ReplayAction(run)) : Collections.<Action>emptySet();
        }

    }

}
