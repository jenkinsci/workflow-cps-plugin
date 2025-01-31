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

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.replay.ReplayAction;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.support.steps.input.InputAction;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStepExecution;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsSessionRule;

/**
 * Tests of pipelines that involve restarting Jenkins in the middle.
 */
public class Workflow2Test {
    @Rule
    public JenkinsSessionRule story = new JenkinsSessionRule();

    @Test
    public void restartAReplayedBuild() throws Throwable {
        story.then(Workflow2Test::stage1);
        story.then(Workflow2Test::stage2);
    }

    /**
     * <li>Create a project with concurrency disabled
     * <li>Schedule a first build, paused waiting for input.
     * <li>Replay it. Since the first build is still running, the replayed build is in queue.
     */
    private static void stage1(JenkinsRule r) throws Throwable {
        WorkflowJob p = r.createProject(WorkflowJob.class, "demo");
        p.setConcurrentBuild(false);
        p.setDefinition(new CpsFlowDefinition("input 'Waiting for approval'", true));
        WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        r.waitForMessage("Waiting for approval", b);
        // Start a replay
        b.getAction(ReplayAction.class).run("input 'Waiting for approval'", new HashMap<>());
    }

    /**
     * After a restart
     * <li>unblock the first build, allowing it to complete
     * <li>check sandbox status for the second build
     */
    private static void stage2(JenkinsRule r) throws Throwable {
        var p = r.jenkins.getItemByFullName("demo", WorkflowJob.class);
        var b = p.getBuildByNumber(1);
        InputAction inputAction = b.getAction(InputAction.class);
        assertNotNull(inputAction);
        assertTrue(inputAction.isWaitingForInput());
        InputStepExecution inputStepExecution = inputAction.getExecutions().get(0);
        assertNotNull(inputStepExecution);
        inputStepExecution.proceed(null);
        r.assertBuildStatusSuccess(r.waitForCompletion(b));
        var b2 = await().until(() -> p.getBuildByNumber(2), Matchers.notNullValue());
        assertThat("Expecting the replayed build to use sandbox", ((CpsFlowExecution) b2.getExecution()).isSandbox(), is(true));
    }
}
