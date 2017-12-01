/*
 * The MIT License
 *
 * Copyright 2015 CloudBees, Inc.
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

import hudson.EnvVars;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class DynamicEnvironmentExpanderTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Issue("JENKINS-26163")
    @Test public void dynamics() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("dynamicEnv {echo \"initially ${env.DYNVAR}\"; semaphore 'wait'; echo \"subsequently ${env.DYNVAR}\"}", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
                story.j.waitForMessage("initially one", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                SemaphoreStep.success("wait/1", null);
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                story.j.assertLogContains("subsequently two", story.j.waitForCompletion(b));
            }
        });
    }
    public static class DynamicEnvStep extends Step {
        @DataBoundConstructor public DynamicEnvStep() {}
        @Override public StepExecution start(StepContext context) throws Exception {
            return new Execution(context);
        }
        private static class Execution extends StepExecution {
            Execution(StepContext context) {
                super(context);
            }
            private static final long serialVersionUID = 1;
            String value;
            @Override public boolean start() throws Exception {
                StepContext context = getContext();
                value = "one";
                context.newBodyInvoker().
                        withContexts(EnvironmentExpander.merge(context.get(EnvironmentExpander.class), new ExpanderImpl(this))).
                        withCallback(BodyExecutionCallback.wrap(context)).
                        start();
                return false;
            }
            @Override public void onResume() {
                super.onResume();
                value = "two";
            }
        }
        private static class ExpanderImpl extends EnvironmentExpander {
            private static final long serialVersionUID = 1;
            // Also works as this$0 from an inner class, but see http://docs.oracle.com/javase/8/docs/platform/serialization/spec/serial-arch.html#a4539 for why that is risky:
            private final Execution execution;
            ExpanderImpl(Execution execution) {
                this.execution = execution;
            }
            @Override public void expand(EnvVars env) throws IOException, InterruptedException {
                env.override("DYNVAR", execution.value);
            }
        }
        @TestExtension("dynamics") public static class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {return "dynamicEnv";}
            @Override public boolean takesImplicitBlockArgument() {return true;}
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }
        }
    }

    @Issue("JENKINS-42499")
    @Test public void changingEnvironment() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("echo(/before VAR=$VAR/); " + EnvAdder.class.getCanonicalName() + ".value = 'after'; echo(/after VAR=$VAR/)", false));
                WorkflowRun b = story.j.buildAndAssertSuccess(p);
                story.j.assertLogContains("buildEnvironmentFor #1", b);
                story.j.assertLogContains("before VAR=before", b);
                story.j.assertLogContains("buildEnvironmentFor #2", b);
                story.j.assertLogContains("after VAR=after", b);
            }
        });
    }
    @TestExtension("changingEnvironment") public static class EnvAdder extends EnvironmentContributor {
        public static String value = "before";
        private int count;
        @SuppressWarnings("rawtypes")
        @Override public void buildEnvironmentFor(Run r, EnvVars envs, TaskListener listener) throws IOException, InterruptedException {
            listener.getLogger().println("buildEnvironmentFor #" + count++);
            envs.put("VAR", value);
        }
    }

}
