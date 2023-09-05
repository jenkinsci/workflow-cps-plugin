/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import groovy.lang.GroovyShell;
import hudson.AbortException;
import hudson.ExtensionList;
import hudson.XmlFile;
import hudson.model.Executor;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.io.File;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.io.FileUtils;
import org.hamcrest.Matchers;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInRelativeOrder;
import org.htmlunit.ElementNotFoundException;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.HttpMethod;
import org.htmlunit.WebRequest;
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.cps.GroovySourceFileAllowlist.DefaultAllowlist;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.support.pickles.SingleTypedPickleFactory;
import org.jenkinsci.plugins.workflow.support.pickles.TryRepeatedly;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.FlagRule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.JenkinsSessionRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

public class CpsFlowExecutionTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsSessionRule sessions = new JenkinsSessionRule();
    @Rule public LoggerRule logger = new LoggerRule();
    @Rule public FlagRule<Boolean> secretField = new FlagRule<>(() -> CpsFlowExecutionTest.SECRET, v -> CpsFlowExecutionTest.SECRET = v);
    // We intentionally avoid using the static fields so that tests can call setProperty before the classes are initialized.
    @Rule public FlagRule<String> groovySourceFileAllowlistDisabled = FlagRule.systemProperty("org.jenkinsci.plugins.workflow.cps.GroovySourceFileAllowlist.DISABLED");
    @Rule public FlagRule<String> groovySourceFileAllowlistFiles = FlagRule.systemProperty("org.jenkinsci.plugins.workflow.cps.GroovySourceFileAllowlist.DefaultAllowlist.ALLOWED_SOURCE_FILES");

    @Test public void getCurrentExecutions() throws Throwable {
        sessions.then(r -> {
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "echo 'a step'; semaphore 'one'; retry(2) {semaphore 'two'; node {semaphore 'three'}; semaphore 'four'}; semaphore 'five'; " +
                        "parallel a: {node {semaphore 'six'}}, b: {semaphore 'seven'}; semaphore 'eight'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("one/1", b);
                FlowExecution e = b.getExecution();
                assertStepExecutions(e, "semaphore");
                SemaphoreStep.success("one/1", null);
                SemaphoreStep.waitForStart("two/1", b);
                assertStepExecutions(e, "retry {}", "semaphore");
                SemaphoreStep.success("two/1", null);
                SemaphoreStep.waitForStart("three/1", b);
                assertStepExecutions(e, "retry {}", "node {}", "semaphore");
        });
        sessions.then(r -> {
                WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                CpsFlowExecution e = (CpsFlowExecution) b.getExecution();
                assertTrue(e.isSandbox());
                SemaphoreStep.success("three/1", null);
                SemaphoreStep.waitForStart("four/1", b);
                assertStepExecutions(e, "retry {}", "semaphore");
                SemaphoreStep.failure("four/1", new AbortException("try again"));
                SemaphoreStep.waitForStart("two/2", b);
                assertStepExecutions(e, "retry {}", "semaphore");
                SemaphoreStep.success("two/2", null);
                SemaphoreStep.waitForStart("three/2", b);
                assertStepExecutions(e, "retry {}", "node {}", "semaphore");
                SemaphoreStep.success("three/2", null);
                SemaphoreStep.waitForStart("four/2", b);
                assertStepExecutions(e, "retry {}", "semaphore");
                SemaphoreStep.success("four/2", null);
                SemaphoreStep.waitForStart("five/1", b);
                assertStepExecutions(e, "semaphore");
                SemaphoreStep.success("five/1", null);
                SemaphoreStep.waitForStart("six/1", b);
                SemaphoreStep.waitForStart("seven/1", b);
                assertStepExecutions(e, "parallel {}", "node {}", "semaphore", "semaphore");
                SemaphoreStep.success("six/1", null);
                SemaphoreStep.success("seven/1", null);
                SemaphoreStep.waitForStart("eight/1", b);
                assertStepExecutions(e, "semaphore");
                SemaphoreStep.success("eight/1", null);
                r.assertBuildStatusSuccess(r.waitForCompletion(b));
                assertStepExecutions(e);
        });
    }
    private static void assertStepExecutions(FlowExecution e, String... steps) throws Exception {
        List<String> current = stepNames(e.getCurrentExecutions(true));
        List<String> all = stepNames(e.getCurrentExecutions(false));
        int allCount = all.size();
        int blockCount = allCount - current.size();
        assertEquals(current + " was not the tail of " + all, current, all.subList(blockCount, allCount));
        ListIterator<String> it = all.listIterator();
        for (int i = 0; i < blockCount; i++) {
            it.set(it.next() + " {}");
        }
        assertEquals(Arrays.toString(steps), all.toString());
    }
    private static List<String> stepNames(ListenableFuture<List<StepExecution>> executionsFuture) throws Exception {
        List<String> r = new ArrayList<>();
        for (StepExecution e : executionsFuture.get()) {
            // TODO should this method be defined in StepContext?
            StepDescriptor d = ((CpsStepContext) e.getContext()).getStepDescriptor();
            assertNotNull(d);
            r.add(d.getFunctionName());
        }
        return r;
    }

    @Issue("JENKINS-25736")
    @Test public void pause() throws Throwable {
        sessions.then(r -> {
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
                r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
                r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                    grant(Jenkins.READ, Item.READ).everywhere().toEveryone().
                    grant(Jenkins.ADMINISTER).everywhere().to("admin").
                    grant(Item.BUILD, Item.CANCEL).onItems(p).to("dev"));
                r.jenkins.save();
                p.setDefinition(new CpsFlowDefinition("echo 'before'; semaphore 'one'; echo 'after'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("one/1", b);
                final CpsFlowExecution e = (CpsFlowExecution) b.getExecution();
                assertFalse(e.isPaused());
                JenkinsRule.WebClient wc = r.createWebClient();
                String toggleUrlRel = b.getUrl() + PauseUnpauseAction.URL + "/toggle";
                WebRequest wrs = new WebRequest(wc.createCrumbedUrl(toggleUrlRel), HttpMethod.POST);
                try { // like JenkinsRule.assertFails but taking a WebRequest:
                    fail("should have been rejected but produced: " + wc.getPage(wrs).getWebResponse().getContentAsString());
                } catch (FailingHttpStatusCodeException x) {
                    assertEquals(HttpURLConnection.HTTP_NOT_FOUND, x.getStatusCode()); // link not even offered
                }
                wc.login("admin").getPage(wrs);
                assertTrue(e.isPaused());
                r.waitForMessage("before", b);
                SemaphoreStep.success("one/1", null);

                // not a very strong way of ensuring that the pause actually happens
                Thread.sleep(1000);
                assertTrue(b.isBuilding());
                assertTrue(e.isPaused());

                // link should only be displayed conditionally:
                String toggleUrlAbs = r.contextPath + "/" + toggleUrlRel;
                r.createWebClient().login("admin").getPage(b).getAnchorByHref(toggleUrlAbs);
                try {
                    r.createWebClient().getPage(b).getAnchorByHref(toggleUrlAbs);
                    fail("link should not be present for anonymous user without CANCEL");
                } catch (ElementNotFoundException x) {
                    // good
                }
        });
        sessions.then(r -> {
                WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                assertTrue(b.isBuilding());
                CpsFlowExecution e = (CpsFlowExecution) b.getExecution();
                assertTrue(e.isPaused());
                JenkinsRule.WebClient wc = r.createWebClient();
                WebRequest wrs = new WebRequest(wc.createCrumbedUrl(b.getUrl() + PauseUnpauseAction.URL + "/toggle"), HttpMethod.POST);
                wc.login("dev").getPage(wrs);
                assertFalse(e.isPaused());
                r.assertBuildStatusSuccess(r.waitForCompletion(b));
                assertFalse(e.isPaused());
        });
    }

    @Issue("JENKINS-32015")
    @Test public void quietDown() throws Throwable {
        sessions.then(r -> {
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("semaphore 'wait'; echo 'I am done'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
                r.jenkins.doQuietDown(true, 0);
                SemaphoreStep.success("wait/1", null);
                r.waitForMessage("Pausing (Preparing for shutdown)", b);
                ((CpsFlowExecution) b.getExecution()).waitForSuspension();
                assertTrue(b.isBuilding());
        });
        sessions.then(r -> {
                WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                r.assertLogContains("I am done", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        });
    }

    @Issue("JENKINS-34256")
    @Test public void quietDownThenCancelQuietDown() throws Throwable {
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition("semaphore 'wait'; echo 'I am done'", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            r.jenkins.doQuietDown(true, 0);
            SemaphoreStep.success("wait/1", null);
            r.waitForMessage("Pausing (Preparing for shutdown)", b);
            assertTrue(b.isBuilding());
            r.assertLogNotContains("I am done", b);
            r.jenkins.doCancelQuietDown();
            r.waitForMessage("Resuming (Shutdown was canceled)", b);
            r.assertLogContains("I am done", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        });
    }

    @Issue("JENKINS-34256")
    @Test public void pauseThenQuietDownThenUnpauseThenCancelQuietDown() throws Throwable {
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition("semaphore 'wait'; echo 'I am done'", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            ((CpsFlowExecution) b.getExecution()).pause(true);
            r.waitForMessage("Pausing", b);
            SemaphoreStep.success("wait/1", null);
            Thread.sleep(1000);
            r.jenkins.doQuietDown(true, 0);
            Thread.sleep(1000);
            r.assertLogNotContains("Pausing (Preparing for shutdown)", b);
            ((CpsFlowExecution) b.getExecution()).pause(false);
            r.waitForMessage("Resuming", b);
            r.waitForMessage("Pausing (Preparing for shutdown)", b);
            r.jenkins.doCancelQuietDown();
            r.waitForMessage("Resuming (Shutdown was canceled)", b);
            r.assertLogContains("I am done", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        });
    }

    @Issue("JENKINS-34256")
    @Test public void pauseThenQuietDownThenCancelQuietDownThenUnpause() throws Throwable {
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition("semaphore 'wait'; echo 'I am done'", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            ((CpsFlowExecution) b.getExecution()).pause(true);
            r.waitForMessage("Pausing", b);
            SemaphoreStep.success("wait/1", null);
            Thread.sleep(1000);
            r.jenkins.doQuietDown(true, 0);
            Thread.sleep(1000);
            r.assertLogNotContains("Pausing (Preparing for shutdown)", b);
            r.jenkins.doCancelQuietDown();
            Thread.sleep(1000);
            r.assertLogNotContains("Resuming (Shutdown was canceled)", b);
            ((CpsFlowExecution) b.getExecution()).pause(false);
            r.waitForMessage("Resuming", b);
            r.assertLogContains("I am done", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        });
    }

    @Issue("JENKINS-34256")
    @Test public void quietDownThenPauseThenCancelQuietDownThenUnpause() throws Throwable {
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition("semaphore 'wait'; echo 'I am done'", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            r.jenkins.doQuietDown(true, 0);
            SemaphoreStep.success("wait/1", null);
            r.waitForMessage("Pausing (Preparing for shutdown)", b);
            ((CpsFlowExecution) b.getExecution()).pause(true);
            r.waitForMessage("Pausing", b);
            r.jenkins.doCancelQuietDown();
            r.waitForMessage("Resuming (Shutdown was canceled)", b);
            r.assertLogNotContains("I am done", b);
            ((CpsFlowExecution) b.getExecution()).pause(false);
            r.waitForMessage("Resuming", b);
            r.assertLogContains("I am done", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        });
    }

    @Issue("JENKINS-34256")
    @Test public void quietDownThenPauseThenUnpauseThenCancelQuietDown() throws Throwable {
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition("semaphore 'wait'; echo 'I am done'", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            r.jenkins.doQuietDown(true, 0);
            SemaphoreStep.success("wait/1", null);
            r.waitForMessage("Pausing (Preparing for shutdown)", b);
            ((CpsFlowExecution) b.getExecution()).pause(true);
            r.waitForMessage("Pausing", b);
            ((CpsFlowExecution) b.getExecution()).pause(false);
            r.waitForMessage("Resuming", b);
            r.assertLogNotContains("I am done", b);
            r.jenkins.doCancelQuietDown();
            r.waitForMessage("Resuming (Shutdown was canceled)", b);
            r.assertLogContains("I am done", r.assertBuildStatusSuccess(r.waitForCompletion(b)));
        });
    }

    @Issue("JENKINS-59743")
    @Test public void restartWhileTemporarilyPaused() throws Throwable {
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("slowToResume()", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            r.waitForMessage("Started…", b);
        });
        sessions.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getBuildByNumber(1);
            r.waitForMessage("Waiting until ready…", b);
        });
        sessions.then(r -> {
            SlowToResume.DescriptorImpl d = ExtensionList.lookupSingleton(SlowToResume.DescriptorImpl.class);
            synchronized (d) {
                d.ready = true;
                d.notifyAll();
            }
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getBuildByNumber(1);
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
        });
    }
    public static final class SlowToResume extends Step {
        @DataBoundConstructor public SlowToResume() {}
        @Override public StepExecution start(StepContext context) throws Exception {
            return new Execution(context);
        }
        private static final class Execution extends StepExecution {
            Execution(StepContext context) {
                super(context);
            }
            @Override public boolean start() throws Exception {
                getContext().get(TaskListener.class).getLogger().println("Started…");
                return false;
            }
            @Override public void onResume() {
                DescriptorImpl d = ExtensionList.lookupSingleton(DescriptorImpl.class);
                synchronized (d) {
                    try {
                        while (!d.ready) {
                            getContext().get(TaskListener.class).getLogger().println("Waiting until ready…");
                            d.wait();
                        }
                        getContext().get(TaskListener.class).getLogger().println("…ready.");
                        getContext().onSuccess(null);
                    } catch (Exception x) {
                        getContext().onFailure(x);
                    }
                }
            }
        }
        @TestExtension("restartWhileTemporarilyPaused") public static final class DescriptorImpl extends StepDescriptor {
            boolean ready;
            @Override public String getFunctionName() {
                return "slowToResume";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Set.of(TaskListener.class);
            }
        }
    }

    @Test public void timing() throws Throwable {
        sessions.then(r -> {
                logger.record(CpsFlowExecution.TIMING_LOGGER, Level.FINE).capture(100);
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("semaphore 'wait'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
        });
        sessions.then(r -> {
                WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                FileUtils.copyFile(new File(b.getRootDir(), "build.xml"), System.out);
                SemaphoreStep.success("wait/1", null);
                r.assertBuildStatusSuccess(r.waitForCompletion(b));
                while (logger.getRecords().isEmpty()) {
                    Thread.sleep(100); // apparently a race condition between CpsVmExecutorService.tearDown and WorkflowRun.finish
                }
                // TODO https://github.com/jenkinsci/workflow-cps-plugin/pull/570#issuecomment-1192679404 message can be duplicated
                assertThat(logger.getRecords(), Matchers.not(Matchers.empty()));
                assertEquals(CpsFlowExecution.TimingKind.values().length, ((CpsFlowExecution) b.getExecution()).liveTimings.keySet().size());
        });
    }

    @Test public void internalCallsAcrossRestart() throws Throwable {
        sessions.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition("currentBuild.rawBuild.description = 'XXX'; semaphore 'wait'; Jenkins.instance.systemMessage = 'XXX'", false));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
        });
        sessions.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getLastBuild();
            assertThat(new XmlFile(new File(b.getRootDir(), "build.xml")).asString(), containsString(
                "<string>org.jenkinsci.plugins.workflow.job.WorkflowRun.description</string>"));
            CpsFlowExecution exec = (CpsFlowExecution) b.getExecution();
            assertThat(exec.getInternalCalls(), contains(
                "org.jenkinsci.plugins.workflow.job.WorkflowRun.description",
                "org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper.rawBuild"));
            SemaphoreStep.success("wait/1", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
            assertThat(exec.getInternalCalls(), contains(
                "hudson.model.Hudson.systemMessage",
                "jenkins.model.Jenkins.instance",
                "org.jenkinsci.plugins.workflow.job.WorkflowRun.description",
                "org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper.rawBuild"));
        });
    }

    @Issue("JENKINS-26130")
    @Test public void interruptProgramLoad() throws Throwable {
        sessions.then(r -> {
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("def x = new " + BadThing.class.getCanonicalName() + "(); semaphore 'wait'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
        });
        sessions.then(r -> {
                Logger LOGGER = Logger.getLogger("org.jenkinsci.plugins.workflow");
                LOGGER.setLevel(Level.FINE);
                Handler handler = new ConsoleHandler();
                handler.setLevel(Level.ALL);
                LOGGER.addHandler(handler);
                WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                assertTrue(b.isBuilding());
                r.waitForMessage("Cannot restore BadThing", b);
                b.getExecutor().interrupt();
                r.assertBuildStatus(Result.ABORTED, r.waitForCompletion(b));
        });
    }
    public static class BadThing {
        @Whitelisted public BadThing() {}
    }
    private static class BadThingPickle extends Pickle {
        @Override public ListenableFuture<?> rehydrate(final FlowExecutionOwner owner) {
            return new TryRepeatedly<BadThing>(1) {
                @Override protected BadThing tryResolve() throws Exception {
                    return null;
                }
                @Override protected FlowExecutionOwner getOwner() {
                    return owner;
                }
                @Override protected void printWaitingMessage(TaskListener listener) {
                    listener.getLogger().println("Cannot restore BadThing");
                }
            };
        }
    }
    @TestExtension("interruptProgramLoad") public static class BadThingPickleFactory extends SingleTypedPickleFactory<BadThing> {
        @Override protected Pickle pickle(BadThing object) {
            return new BadThingPickle();
        }
    }

    @Test public void trustedShell() throws Throwable {
        trustedShell(true);
    }

    @Test public void trustedShell_control() throws Throwable {
        trustedShell(false);
    }

    /**
     * Insert trusted/ dir into the trusted shell to enable trusted code execution
     */
    @TestExtension("trustedShell")
    public static class TrustedShell extends GroovyShellDecorator {
        @Override
        public GroovyShellDecorator forTrusted() {
            return new UntrustedShellDecorator();
        }
    }

    @TestExtension("trustedShell_control")
    public static class UntrustedShellDecorator extends GroovyShellDecorator {
        @Override
        public void configureShell(@CheckForNull CpsFlowExecution context, GroovyShell shell) {
            try {
                URL u = TrustedShell.class.getClassLoader().getResource("trusted/foo.groovy");
                shell.getClassLoader().addURL(new URL(u,"."));
            } catch (MalformedURLException e) {
                throw new AssertionError(e);
            }
        }
    }

    private void trustedShell(final boolean pos) throws Throwable {
        sessions.then(r -> {
                WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("new foo().attempt()", true));
                WorkflowRun b = p.scheduleBuild2(0).get();
                if (pos) {
                    r.assertBuildStatusSuccess(b);
                    assertTrue(SECRET);
                } else {
                    // should have failed with RejectedAccessException trying to touch 'SECRET'
                    r.assertBuildStatus(Result.FAILURE, b);
                    r.assertLogContains(
                            new RejectedAccessException("staticField",CpsFlowExecutionTest.class.getName()+" SECRET").getMessage(),
                            b);
                    assertFalse(SECRET);
                }
        });
    }

    /**
     * This field shouldn't be visible to regular script.
     */
    public static boolean SECRET;

    @Issue("SECURITY-359")
    @Test public void groovySourcesCannotBeUsedByDefault() throws Throwable {
        logger.record(GroovySourceFileAllowlist.class, Level.INFO).capture(100);
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition(
                    "new hudson.model.View.main()", true));
            WorkflowRun b = r.buildAndAssertStatus(Result.FAILURE, p);
            r.assertLogContains("unable to resolve class hudson.model.View.main", b);
            assertThat(logger.getMessages(), hasItem(containsString("/hudson/model/View/main.groovy from being loaded without sandbox protection in " + b)));
        });
    }

    @Issue("SECURITY-359")
    @Test public void groovySourcesCanBeUsedIfAllowlistIsDisabled() throws Throwable {
        System.setProperty("org.jenkinsci.plugins.workflow.cps.GroovySourceFileAllowlist.DISABLED", "true");
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition(
                    "new hudson.model.View.main()", true));
            WorkflowRun b = r.buildAndAssertSuccess(p);
        });
    }

    @Issue("SECURITY-359")
    @Test public void groovySourcesCanBeUsedIfAddedToSystemProperty() throws Throwable {
        System.setProperty("org.jenkinsci.plugins.workflow.cps.GroovySourceFileAllowlist.DefaultAllowlist.ALLOWED_SOURCE_FILES", "/just/an/example.groovy,/hudson/model/View/main.groovy");
        logger.record(DefaultAllowlist.class, Level.INFO).capture(100);
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition(
                    "new hudson.model.View.main()", true));
            WorkflowRun b = r.buildAndAssertSuccess(p);
            assertThat(logger.getMessages(), hasItem(containsString("Allowing Pipelines to access /hudson/model/View/main.groovy")));
        });
    }

    @Issue("SECURITY-359")
    @Test public void groovySourcesCanBeUsedIfAllowed() throws Throwable {
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition(
                    "(new trusted.foo()).attempt()", true));
            WorkflowRun b = r.buildAndAssertSuccess(p);
            assertTrue(SECRET);
        });
    }

    @TestExtension("groovySourcesCanBeUsedIfAllowed")
    public static class TestAllowlist extends GroovySourceFileAllowlist {
        @Override
        public boolean isAllowed(String groovyResourceUrl) {
            return groovyResourceUrl.endsWith("/trusted/foo.groovy");
        }
    }

    @Issue({ "JENKINS-45327", "JENKINS-68849" })
    @Test public void envActionImplPickle() throws Throwable {
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                    "def e = env\n" +
                    "semaphore('wait')\n" + // An instance of EnvActionImpl is part of the program's state at this point.
                    "e.foo = 'bar'\n" +  // Without EnvActionImplPickle, this throws an NPE in EnvActionImpl.setProperty because owner is null.
                    "env.getProperty('foo')\n", true)); // Without EnvActionImpl.readResolve, this throws an NPE in PogoMetaClassSite.call
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
        });
        sessions.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getLastBuild();
            SemaphoreStep.success("wait/1", null);
            r.assertBuildStatus(Result.SUCCESS, r.waitForCompletion(b));
            assertThat(EnvActionImpl.forRun(b).getEnvironment().get("foo"), equalTo("bar"));
        });
    }

    @Test public void suspendOrder() throws Throwable {
        System.setProperty(Jenkins.class.getName() + "." + "termLogLevel", "INFO");
        logger.record(CpsFlowExecution.class, Level.FINE).record(FlowExecutionList.class, Level.FINE).capture(100);
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class);
            p.setDefinition(new CpsFlowDefinition("echo 'ok'", true));
            r.buildAndAssertSuccess(p);
        });
        assertThat(logger.getMessages(), containsInRelativeOrder("finished suspending all executions", "ensuring all executions are saved"));
    }

    @Issue("JENKINS-71692")
    @Test public void stepsAreStoppedWhenCpsVmExecutorServiceHandlesUncaughtException() throws Throwable {
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                "parallel(\n" +
                "  'willCroak': {\n" +
                "    node {\n" +
                "      semaphore('croak')\n" +
                "    }\n" +
                "  },\n" +
                "  'keepsRunning': {\n" +
                "    semaphore('other')\n" +
                "    echo 'kept running!'\n" +
                "  }\n" +
                ")", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("croak/1", b);
            SemaphoreStep.waitForStart("other/1", b);
            ((CpsFlowExecution) b.getExecution()).runInCpsVmThread(new FutureCallback<>() {
                @Override
                public void onSuccess(CpsThreadGroup g) {
                    // In practice this would be some kind of unhandled exception in groovy-cps.
                    throw new IllegalStateException("Failure in CPS VM thread!");
                }
                @Override
                public void onFailure(Throwable t) { }
            });
            r.assertBuildStatus(Result.FAILURE, r.waitForCompletion(b));
            r.assertLogContains("Failure in CPS VM thread!", b);
            r.assertLogContains("Terminating parallel (id: 3)", b);
            r.assertLogContains("Terminating node (id: 7)", b);
            r.assertLogContains("Terminating semaphore (id: 8)", b);
            for (Executor executor : Jenkins.get().toComputer().getExecutors()) {
                // Node step should have cleaned up its PlaceholderExecutable.
                assertThat(executor.getCurrentWorkUnit(), nullValue());
            }
            // Simulate some async steps completing after CpsFlowExecution.croak.
            SemaphoreStep.success("croak/1", null);
            SemaphoreStep.success("other/1", null);
            // It's difficult to add meaningful non-flaky test assertions here.
            // Even before the associated fix, 'kept running' wasn't printed, because although CpsThreadGroup.run
            // did execute again, it hit an IllegalStateException in SandboxContinuable, which resulted in a second
            // call to CpsFlowExecution.croak.
            assertTrue(((CpsFlowExecution) b.getExecution()).programPromise.get(1, TimeUnit.SECONDS).runner.isShutdown());
        });
    }

    @Issue("JENKINS-70267")
    @Test public void stepsAreStoppedWhenBodyExecutionCallbackThrows() throws Throwable {
        sessions.then(r -> {
            WorkflowJob p = r.createProject(WorkflowJob.class, "p");
            p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  badBodyCallback() { }\n" +
                "}\n", true));
            WorkflowRun b = r.buildAndAssertStatus(Result.FAILURE, p);
            r.assertLogContains("Exception in onSuccess", b);
            for (Executor executor : Jenkins.get().toComputer().getExecutors()) {
                // Node step should have cleaned up its PlaceholderExecutable.
                assertThat(executor.getCurrentWorkUnit(), nullValue());
            }
            p.setDefinition(new CpsFlowDefinition(
                "node {\n" +
                "  badBodyCallback() { throw new Exception('original') }\n" +
                "}\n", false));
            b = r.buildAndAssertStatus(Result.FAILURE, p);
            r.assertLogContains("Exception in onFailure", b);
            r.assertLogContains("Exception: original", b);
            for (Executor executor : Jenkins.get().toComputer().getExecutors()) {
                // Node step should have cleaned up its PlaceholderExecutable.
                assertThat(executor.getCurrentWorkUnit(), nullValue());
            }
        });
    }

    public static class BadBodyCallback extends Step implements Serializable {
        private static final long serialVersionUID = 1L;
        @DataBoundConstructor
        public BadBodyCallback() { }
        @Override
        public StepExecution start(StepContext context) throws Exception {
            return new StepExecution(context) {
                @Override public boolean start() throws Exception {
                    StepContext context = getContext();
                    BodyInvoker invoker = context.newBodyInvoker();
                    invoker.withCallback(new BodyExecutionCallback() {
                        @Override
                        public void onSuccess(StepContext context, Object result) {
                            throw new IllegalStateException("Exception in onSuccess");
                        }
                        @Override
                        public void onFailure(StepContext context, Throwable t) {
                            throw new IllegalStateException("Exception in onFailure");
                        }
                    }).start();
                    return false;
                }
            };
        }
        @TestExtension
        public static class DescriptorImpl extends StepDescriptor {
            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Set.of();
            }
            @Override
            public String getFunctionName() {
                return "badBodyCallback";
            }
            @Override
            public boolean takesImplicitBlockArgument() {
                return true;
            }
        }
    }

}
