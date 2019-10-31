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

import com.google.common.collect.ImmutableSet;
import hudson.Extension;
import hudson.model.Action;
import hudson.model.InvisibleAction;
import hudson.model.Queue;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import hudson.model.Run;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsFlowFactoryAction2;
import org.jenkinsci.plugins.workflow.flow.DurabilityHintProvider;
import org.jenkinsci.plugins.workflow.flow.FlowDefinition;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.GlobalDefaultFlowDurabilityLevel;

/**
 * Attached to a run that is a replay of an earlier one.
 */
class ReplayFlowFactoryAction extends InvisibleAction implements CpsFlowFactoryAction2, Queue.QueueAction {

    private String replacementMainScript;
    private final Map<String,String> replacementLoadedScripts;
    private transient final boolean sandbox;
    
    ReplayFlowFactoryAction(@Nonnull String replacementMainScript, @Nonnull Map<String,String> replacementLoadedScripts, boolean sandbox) {
        this.replacementMainScript = replacementMainScript;
        this.replacementLoadedScripts = new HashMap<>(replacementLoadedScripts);
        this.sandbox = sandbox;
    }

    @Override public CpsFlowExecution create(FlowDefinition def, FlowExecutionOwner owner, List<? extends Action> actions) throws IOException {
        String script = replacementMainScript;
        replacementMainScript = null; // minimize build.xml size
        Queue.Executable exec = owner.getExecutable();
        FlowDurabilityHint hint = (exec instanceof Run) ? DurabilityHintProvider.suggestedFor(((Run)exec).getParent()) : GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint();
        return new CpsFlowExecution(script, sandbox, owner, hint);
    }

    @Override public boolean shouldSchedule(List<Action> actions) {
        return true; // do not coalesce
    }

    Set<String> replaceableScripts() {
        return ImmutableSet.copyOf(replacementLoadedScripts.keySet());
    }

    @CheckForNull String replace(String clazz) {
        return replacementLoadedScripts.remove(clazz);
    }

    @Extension public static class StoredLoadedScripts extends OriginalLoadedScripts {

        @Override public Map<String, String> loadScripts(CpsFlowExecution execution) {
            return execution.getLoadedScripts();
        }

    }

}
