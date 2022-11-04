/*
 * The MIT License
 *
 * Copyright (c) 2019, CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow;

import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;

import edu.umd.cs.findbugs.annotations.NonNull;

public class StepListenerTest {
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Issue("JENKINS-58084")
    @Test
    public void listener() throws Exception {
        WorkflowJob job = r.createProject(WorkflowJob.class, "listener");
        job.setDefinition(new CpsFlowDefinition("node {\n" +
                "  echo 'hi'\n" +
                "}\n", true));
        WorkflowRun build = r.buildAndAssertSuccess(job);
        r.assertLogContains("Step listener saw step node", build);
        r.assertLogContains("Step listener saw step echo", build);
    }

    @TestExtension
    public static class TestStepListener implements StepListener {
        @Override
        public void notifyOfNewStep(@NonNull Step s, @NonNull StepContext context) {
            try {
                TaskListener listener = context.get(TaskListener.class);
                if (listener == null) {
                    listener = TaskListener.NULL;
                }
                listener.getLogger().println("Step listener saw step " + s.getDescriptor().getFunctionName());
            } catch (Exception e) {
                // Don't care.
            }
        }
    }
}
