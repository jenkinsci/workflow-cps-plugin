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

package org.jenkinsci.plugins.workflow.cps;

import hudson.Extension;
import hudson.model.Action;
import hudson.model.Item;
import hudson.security.AccessControlled;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import jenkins.model.TransientActionFactory;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

/**
 * Adds actions to the sidebar of a running Pipeline build.
 */
@Extension public class RunningFlowActions extends TransientActionFactory<FlowExecutionOwner.Executable> {
    
    @Override public Class<FlowExecutionOwner.Executable> type() {
        return FlowExecutionOwner.Executable.class;
    }
    
    @Override public Collection<? extends Action> createFor(FlowExecutionOwner.Executable executable) {
        FlowExecutionOwner owner = executable.asFlowExecutionOwner();
        if (owner != null) {
            FlowExecution exec = owner.getOrNull();
            if (exec instanceof CpsFlowExecution && !exec.isComplete()) {
                CpsFlowExecution e = (CpsFlowExecution) exec;
                List<Action> actions = new ArrayList<>();
                actions.add(new CpsThreadDumpAction(e));
                // TODO cf. comment in CpsFlowExecution#pause
                if (!(executable instanceof AccessControlled) || ((AccessControlled) executable).hasPermission(Item.CANCEL)) {
                    actions.add(new PauseUnpauseAction(e));
                }
                return actions;
            }
        }
        return Collections.emptySet();
    }

}
