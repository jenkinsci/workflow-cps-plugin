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

package org.jenkinsci.plugins.workflow;

import hudson.model.queue.QueueTaskFuture;
import hudson.slaves.DumbSlave;
import jenkins.model.Jenkins;
import org.apache.commons.lang3.StringUtils;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;

/**
 * Base class for tests that interacts with a single workflow job named {@code demo}.
 *
 * @author Kohsuke Kawaguchi
 * @deprecated inline relevant parts
 */
@Deprecated
public abstract class SingleJobTestBase extends Assert {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    // currently executing workflow and its build
    public WorkflowJob p;
    public WorkflowRun b;
    public CpsFlowExecution e;

    /**
     * Updates p, b, and e variables from the given JenkinsRule
     */
    public void rebuildContext(JenkinsRule j) throws Exception {
        WorkflowJob p2 = (WorkflowJob) j.jenkins.getItem("demo");
        assertNotNull("could not find a job named demo", p2);
        assert p != p2; // make sure Jenkins was restarted
        p = p2;

        WorkflowRun b2 = p.getLastBuild();
        assert b != b2;
        b = b2;

        e = (CpsFlowExecution) b.getExecution();
    }

    /** @deprecated use {@link JenkinsRule#waitForCompletion} instead */
    @Deprecated
    public void waitForWorkflowToComplete() throws Exception {
        do {
            waitForWorkflowToSuspend(e);
        } while (!e.isComplete());
    }

    /** @deprecated Use some other idiom, like {@link SemaphoreStep}. */
    @Deprecated
    public void waitForWorkflowToSuspend() throws Exception {
        waitForWorkflowToSuspend(e);
    }

    public void waitForWorkflowToSuspend(CpsFlowExecution e) throws Exception {
        e.waitForSuspension();
    }

    /**
     * Gets the build going and waits for the workflow to be fully running
     */
    public QueueTaskFuture<WorkflowRun> startBuilding() throws Exception {
        QueueTaskFuture<WorkflowRun> f = p.scheduleBuild2(0);
        b = f.waitForStart();
        e = (CpsFlowExecution) b.getExecutionPromise().get();
        return f;
    }

    public DumbSlave createSlave(JenkinsRule j) throws Exception {
        DumbSlave s = j.createSlave();
        s.getComputer().connect(false).get(); // wait for the agent to fully get connected
        return s;
    }

    /**
     * Verifies that 'b' has completed successfully.
     */
    public void assertBuildCompletedSuccessfully() throws Exception {
        assertBuildCompletedSuccessfully(b);
    }

    public void assertBuildCompletedSuccessfully(WorkflowRun b) throws Exception {
        assert !b.isBuilding();
        story.j.assertBuildStatusSuccess(b);
    }

    public Jenkins jenkins() {
        return story.j.jenkins;
    }

    public String join(String... args) {
        return StringUtils.join(args, "\n");
    }
}
