/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps.actions;

import org.jenkinsci.plugins.workflow.actions.PersistentAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

/**
 * Action that captures the actual {@link Step} run in a node, for deeper inspection
 */
public class StepAction implements PersistentAction {

    private Step step;

    @Nonnull
    public Step getStep() {
        return step;
    }

    /**
     * Get the {@link Step} run for a {@link FlowNode}, or null if not stored
     * @param n FlowNode to look for the step of
     * @return Step run or null
     */
    @CheckForNull
    public static Step getNodeStep(@Nonnull FlowNode n) {
        StepAction sa = n.getPersistentAction(StepAction.class);
        return (sa != null) ? sa.step : null;
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "StepInfo";
    }

    @Override
    public String getUrlName() {
        return "step";
    }

    public StepAction(Step myStep) {
        this.step = myStep;
    }
}
