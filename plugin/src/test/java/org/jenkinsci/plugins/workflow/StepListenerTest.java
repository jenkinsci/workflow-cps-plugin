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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.model.TaskListener;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.flow.StepListener;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class StepListenerTest {
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Issue("JENKINS-58084")
    @Test
    public void listener() throws Exception {
        WorkflowJob job = r.createProject(WorkflowJob.class, "listener");
        job.setDefinition(new CpsFlowDefinition("node {\n" + "  echo 'hi'\n" + "}\n", true));
        WorkflowRun build = r.buildAndAssertSuccess(job);
        r.assertLogContains("Step listener saw step node", build);
        r.assertLogContains("Step listener saw step echo", build);
    }

    @Test
    public void aroundStart() throws Exception {
        AroundStartListener.EVENTS.clear();
        WorkflowJob job = r.createProject(WorkflowJob.class, "aroundStart");
        job.setDefinition(new CpsFlowDefinition(
                "aroundStartProbe 'completes'\n"
                        + "semaphore 'aroundStart'\n"
                        + "catchError(buildResult: 'SUCCESS') { aroundStartProbe 'throws' }\n"
                        + "catchError(buildResult: 'SUCCESS') { aroundStartProbe 'vetoed' }\n",
                true));
        WorkflowRun build = job.scheduleBuild2(0).waitForStart();
        SemaphoreStep.waitForStart("aroundStart/1", build);
        // The semaphore step has not completed, but its start returned
        for (int i = 0; i < 100 && !AroundStartListener.EVENTS.contains("close semaphore"); i++) {
            Thread.sleep(100);
        }
        assertThat(AroundStartListener.EVENTS, hasItem("close semaphore"));
        SemaphoreStep.success("aroundStart/1", null);
        r.assertBuildStatusSuccess(r.waitForCompletion(build));
        assertThat(
                AroundStartListener.EVENTS,
                contains(
                        "open aroundStartProbe",
                        "aroundStartProbe completes sees aroundStartProbe",
                        "close aroundStartProbe",
                        "open semaphore",
                        "close semaphore",
                        "open catchError",
                        "close catchError",
                        "open aroundStartProbe",
                        "aroundStartProbe throws sees aroundStartProbe",
                        "close aroundStartProbe",
                        "open catchError",
                        "close catchError"));
    }

    /**
     * Sets {@link #CURRENT} around the start of each step, and records when it does it and whether it finds the thread
     * and the value it set when it closes. Makes {@code aroundStartProbe 'vetoed'} fail before it starts.
     */
    @TestExtension
    public static class AroundStartListener implements StepListener {
        static final List<String> EVENTS = new CopyOnWriteArrayList<>();
        static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

        @Override
        public void notifyOfNewStep(@NonNull Step s, @NonNull StepContext context) {
            if (s instanceof AroundStartProbeStep
                    && ((AroundStartProbeStep) s).getId().equals("vetoed")) {
                context.onFailure(new AbortException("vetoed by a step listener"));
            }
        }

        @Override
        public AutoCloseable aroundStart(@NonNull Step s, @NonNull StepContext context) {
            String name = s.getDescriptor().getFunctionName();
            Thread thread = Thread.currentThread();
            CURRENT.set(name);
            EVENTS.add("open " + name);
            return () -> {
                String current = CURRENT.get();
                EVENTS.add("close " + name
                        + (Thread.currentThread() == thread ? "" : " on another thread")
                        + (name.equals(current) ? "" : " with " + current + " current"));
                CURRENT.remove();
            };
        }
    }

    /** Records what {@link AroundStartListener#CURRENT} is when it starts; {@code 'throws'} then throws. */
    public static class AroundStartProbeStep extends Step {
        private final String id;

        @DataBoundConstructor
        public AroundStartProbeStep(String id) {
            this.id = id;
        }

        public String getId() {
            return id;
        }

        @Override
        public StepExecution start(StepContext context) {
            return new Execution(context, id);
        }

        private static final class Execution extends StepExecution {
            private static final long serialVersionUID = 1L;
            private final String id;

            Execution(StepContext context, String id) {
                super(context);
                this.id = id;
            }

            @Override
            public boolean start() throws Exception {
                AroundStartListener.EVENTS.add("aroundStartProbe " + id + " sees " + AroundStartListener.CURRENT.get());
                if (id.equals("throws")) {
                    throw new AbortException("thrown by the start of aroundStartProbe");
                }
                getContext().onSuccess(null);
                return true;
            }
        }

        @TestExtension
        public static class DescriptorImpl extends StepDescriptor {
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Set.of();
            }

            @Override
            public String getFunctionName() {
                return "aroundStartProbe";
            }
        }
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
                listener.getLogger()
                        .println("Step listener saw step " + s.getDescriptor().getFunctionName());
            } catch (Exception e) {
                // Don't care.
            }
        }
    }
}
