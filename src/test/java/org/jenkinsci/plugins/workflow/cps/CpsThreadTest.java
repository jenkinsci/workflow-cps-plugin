/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
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

import hudson.AbortException;
import hudson.model.Result;
import hudson.security.ACL;
import java.util.List;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class CpsThreadTest {

    @ClassRule public static BuildWatcher watcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void stop() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("unkillable()", true));
        final WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        r.waitForMessage("unkillable", b);
        ACL.impersonate(Jenkins.ANONYMOUS, new Runnable() {
            @Override public void run() {
                b.getExecutor().interrupt();
            }
        });
        r.waitForCompletion(b);
        r.assertBuildStatus(Result.ABORTED, b);
        InterruptedBuildAction iba = b.getAction(InterruptedBuildAction.class);
        assertNotNull(iba);
        List<CauseOfInterruption> causes = iba.getCauses();
        assertEquals(1, causes.size());
        assertEquals(CauseOfInterruption.UserInterruption.class, causes.get(0).getClass());
        r.waitForMessage("Finished: ABORTED", b); // TODO JENKINS-46076 WorkflowRun.isBuilding() can go to false before .finish has completed
        r.assertLogContains("never going to stop", b);
        r.assertLogNotContains("\tat ", b);
    }

    public static class UnkillableStep extends AbstractStepImpl {
        @DataBoundConstructor public UnkillableStep() {}
        public static class Execution extends AbstractStepExecutionImpl {
            @Override public boolean start() throws Exception {
                return false;
            }
            @Override public void stop(Throwable cause) throws Exception {
                throw new AbortException("never going to stop");
            }
        }
        @TestExtension public static class DescriptorImpl extends AbstractStepDescriptorImpl {
            public DescriptorImpl() {
                super(Execution.class);
            }
            @Override public String getFunctionName() {
                return "unkillable";
            }
        }
    }

}
