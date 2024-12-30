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

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.ExtensionList;
import hudson.model.Result;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.CpsThreadGroup;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;

import java.util.logging.Level;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;

public class StepListenerTest {
    @Rule
    public JenkinsRule r = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public LoggerRule logger = new LoggerRule();

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

    @TestExtension("listener")
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

    @Issue("JENKINS-75067")
    @Test
    public void failingListener() throws Exception {
        // Even before the fix there's only one warning logged.  Asserting zero records is probably over-stepping,
        // but asserting just one record with our target message risks a false negative (some other unrelated message
        // being first, and our being later).
        logger.record(CpsThreadGroup.class, Level.WARNING).capture(10);
        WorkflowJob job = r.createProject(WorkflowJob.class, "failingListener");
        job.setDefinition(new CpsFlowDefinition("node {}\n", true));

        WorkflowRun build = r.buildAndAssertStatus(Result.FAILURE, job);
        r.assertLogContains("oops", build);
        assertThat(TestFailingStepListener.get().closureCount, equalTo(0));
        assertThat(logger.getMessages(), not(hasItem(containsString("Stale closure"))));
    }

    @TestExtension("failingListener")
    public static class TestFailingStepListener implements StepListener, GraphListener.Synchronous {
        int closureCount = -1;

        @Override
        public void notifyOfNewStep(@NonNull Step s, @NonNull StepContext context) {
            context.onFailure(new AbortException("oops"));
        }

        @Override
        public void onNewHead(FlowNode node) {
            // this only works using a Synchronous listener, otherwise the fall-back closure cleaning
            // will have already executed prior to receiving this event
            if (node instanceof FlowEndNode) {
              try {
                closureCount = ((CpsFlowExecution) node.getExecution()).programPromise.get().closures.size();
              }
              catch (Exception e) {
                throw new RuntimeException(e);
              }
            }
        }

        static TestFailingStepListener get() {
            return ExtensionList.lookupSingleton(TestFailingStepListener.class);
        }
    }
}
