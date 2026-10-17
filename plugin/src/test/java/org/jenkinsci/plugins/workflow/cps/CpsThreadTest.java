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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.google.common.util.concurrent.FutureCallback;
import hudson.AbortException;
import hudson.model.Result;
import hudson.security.ACL;
import hudson.security.ACLContext;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import jenkins.model.CauseOfInterruption;
import jenkins.model.InterruptedBuildAction;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class CpsThreadTest {

    @ClassRule
    public static BuildWatcher watcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void stop() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("unkillable()", true));
        final WorkflowRun b = p.scheduleBuild2(0).waitForStart();
        r.waitForMessage("unkillable", b);
        try (ACLContext context = ACL.as(Jenkins.ANONYMOUS)) {
            b.getExecutor().interrupt();
        }
        r.waitForCompletion(b);
        r.assertBuildStatus(Result.ABORTED, b);
        InterruptedBuildAction iba = b.getAction(InterruptedBuildAction.class);
        assertNotNull(iba);
        List<CauseOfInterruption> causes = iba.getCauses();
        assertEquals(1, causes.size());
        assertEquals(CauseOfInterruption.UserInterruption.class, causes.get(0).getClass());
        // TODO JENKINS-46076 WorkflowRun.isBuilding() can go to false before .finish has completed
        r.waitForMessage("Finished: ABORTED", b);
        r.assertLogContains("never going to stop", b);
        r.assertLogNotContains("\tat ", b);
    }

    /**
     * Reproduces the race where a step has already recorded its outcome (e.g. a remote
     * step just responded), but the queued task that clears {@link CpsThread#getStep()}
     * and resumes the thread has not run yet, because the CPS VM thread was busy with
     * other work. If an interrupt (from {@code failFast}, {@code timeout}, or a manual
     * abort) is queued behind that busy period, {@link CpsThread#stop} used to see
     * {@code getStep() != null} and delegate to the step, whose
     * {@link CpsStepContext#onFailure} then silently discarded the interrupt as a
     * duplicate outcome - the step resumed with its original success and kept running.
     *
     * <p>The ordering here is controlled directly rather than via timing: the CPS VM
     * thread is occupied by a task that blocks on a latch, then the cancellation and the
     * step completion are queued behind it in the order that reproduces the race, and
     * finally the latch is released.
     */
    @Issue("https://github.com/jenkinsci/workflow-cps-plugin/issues/1333")
    @Test
    public void interruptNotLostWhenStepAlreadyCompleted() throws Exception {
        var p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("semaphore 'victim'", true));
        var b = p.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("victim/1", b);

        var victim = new AtomicReference<SemaphoreStep.Execution>();
        StepExecution.acceptAll(SemaphoreStep.Execution.class, exec -> victim.set(exec))
                .get();
        assertNotNull(victim.get());

        var execution = (CpsFlowExecution) b.getExecution();
        var hold = new CountDownLatch(1);

        // Occupy the single CPS VM thread so the cancellation (queued next) and the
        // processing of the step completion (queued after that) pile up behind it.
        execution.runInCpsVmThread(new FutureCallback<CpsThreadGroup>() {
            @Override
            public void onSuccess(CpsThreadGroup g) {
                try {
                    hold.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            @Override
            public void onFailure(Throwable t) {}
        });

        // Queue the cancellation while the CPS VM thread is still occupied - this
        // mirrors what ParallelStepExecution.stop() (failFast) or
        // TimeoutStepExecution.cancel() do under the hood.
        var cause = new FlowInterruptedException(Result.ABORTED);
        execution.runInCpsVmThread(new FutureCallback<CpsThreadGroup>() {
            @Override
            public void onSuccess(CpsThreadGroup g) {
                for (CpsThread t : g.getThreads()) {
                    t.stop(cause);
                }
            }

            @Override
            public void onFailure(Throwable t) {}
        });

        // The step "responds" now, from outside the CPS VM thread, exactly like a
        // remote sh step would: the outcome is recorded synchronously here on the live
        // step context, while the task that clears getStep() and resumes the thread is
        // only queued now - after the cancellation above.
        victim.get().getContext().onSuccess(null);

        // Let the CPS VM thread process the queued cancellation and completion tasks.
        hold.countDown();

        r.waitForCompletion(b);
        r.assertBuildStatus(Result.ABORTED, b);
    }

    public static class UnkillableStep extends AbstractStepImpl {
        @DataBoundConstructor
        public UnkillableStep() {}

        public static class Execution extends AbstractStepExecutionImpl {
            @Override
            public boolean start() throws Exception {
                return false;
            }

            @Override
            public void stop(Throwable cause) throws Exception {
                throw new AbortException("never going to stop");
            }
        }

        @TestExtension
        public static class DescriptorImpl extends AbstractStepDescriptorImpl {
            public DescriptorImpl() {
                super(Execution.class);
            }

            @Override
            public String getFunctionName() {
                return "unkillable";
            }
        }
    }
}
