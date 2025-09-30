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

import static org.junit.Assert.assertEquals;

import hudson.model.Result;
import hudson.model.queue.QueueTaskFuture;
import hudson.util.OneShotEvent;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

/**
 * Test of {@link WorkflowJob} that doesn't involve Jenkins restarts.
 *
 * @author Kohsuke Kawaguchi
 */
public class WorkflowJobNonRestartingTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @ClassRule
    public static JenkinsRule jenkins = new JenkinsRule();

    /**
     * If a prohibited method is called, execution should fail.
     */
    @Issue("JENKINS-26541")
    @Test
    public void sandboxRejection() throws Exception {
        assertRejected("Jenkins.getInstance()");
        assertRejected("parallel(main: {Jenkins.getInstance()})");
        assertRejected("parallel(main: {parallel(main2: {Jenkins.getInstance()})})");
        assertRejected("node {parallel(main: {ws {parallel(main2: {ws {Jenkins.getInstance()}})}})}");
    }

    private void assertRejected(String script) throws Exception {
        String signature = "staticMethod jenkins.model.Jenkins getInstance";
        ScriptApproval scriptApproval = ScriptApproval.get();
        scriptApproval.denySignature(signature);
        assertEquals(Collections.emptySet(), scriptApproval.getPendingSignatures());
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(script, true));
        WorkflowRun b = p.scheduleBuild2(0).get();
        jenkins.assertLogContains(
                "org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use "
                        + signature,
                b);
        jenkins.assertBuildStatus(Result.FAILURE, b);
        Set<ScriptApproval.PendingSignature> pendingSignatures = scriptApproval.getPendingSignatures();
        assertEquals(script, 1, pendingSignatures.size());
        assertEquals(signature, pendingSignatures.iterator().next().signature);
    }

    /**
     * Trying to run a step without having the required context should result in a graceful error.
     */
    @Test
    public void missingContextCheck() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("readFile 'true'", true));

        WorkflowRun b = p.scheduleBuild2(0).get();

        // make sure the 'node' is a suggested message. this comes from MissingContextVariableException
        jenkins.assertLogContains("such as: node", b);
        //        jenkins.assertLogNotContains("Exception", b)   // haven't figured out how to hide this
        jenkins.assertBuildStatus(Result.FAILURE, b);
    }

    @Test
    @Issue("JENKINS-25623")
    public void killInfiniteLoop() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "while(true) { " + WorkflowJobNonRestartingTest.class.getName() + ".going(); }", true));

        QueueTaskFuture<WorkflowRun> f = p.scheduleBuild2(0);
        WorkflowRun b = f.getStartCondition().get(3, TimeUnit.SECONDS);

        going.block(3000); // get the buld going, which will loop infinitely
        b.doStop(); // abort, abort!

        jenkins.assertBuildStatus(Result.ABORTED, jenkins.waitForCompletion(b));
    }

    @Test
    @Issue("JENKINS-25623")
    public void timeoutKillsLoop() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("timeout(time:3, unit:'SECONDS') { while (true) {} }", true));
        jenkins.assertBuildStatus(Result.ABORTED, p.scheduleBuild2(0));
    }

    @Whitelisted
    public static void going() {
        going.signal();
    }

    private static final OneShotEvent going = new OneShotEvent();
}
