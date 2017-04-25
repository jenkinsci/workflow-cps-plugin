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

import com.gargoylesoftware.htmlunit.FailingHttpStatusCodeException;
import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.google.common.util.concurrent.ListenableFuture;
import groovy.lang.GroovyShell;
import groovy.lang.MetaClass;
import hudson.AbortException;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.TaskListener;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;
import org.codehaus.groovy.reflection.ClassInfo;
import org.codehaus.groovy.transform.ASTTransformationVisitor;
import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.support.pickles.SingleTypedPickleFactory;
import org.jenkinsci.plugins.workflow.support.pickles.TryRepeatedly;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MemoryAssert;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;

public class CpsFlowExecutionTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();
    @Rule public LoggerRule logger = new LoggerRule();
    
    private static final List<WeakReference<ClassLoader>> LOADERS = new ArrayList<>();
    public static void register(Object o) {
        ClassLoader loader = o.getClass().getClassLoader();
        System.err.println("registering " + o + " from " + loader);
        LOADERS.add(new WeakReference<>(loader));
    }
    @Test public void loaderReleased() {
        logger.record(CpsFlowExecution.class, Level.FINER);
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                story.j.jenkins.getWorkspaceFor(p).child("lib.groovy").write(CpsFlowExecutionTest.class.getName() + ".register(this)", null);
                p.setDefinition(new CpsFlowDefinition(CpsFlowExecutionTest.class.getName() + ".register(this); node {load 'lib.groovy'}", false));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                assertFalse(((CpsFlowExecution) b.getExecution()).getProgramDataFile().exists());
                assertFalse(LOADERS.isEmpty());
                try {
                    // In Groovy 1.8.9 this keeps static state, but only for the last script (as also noted in JENKINS-23762).
                    // The fix of GROOVY-5025 (62bfb68) in 1.9 addresses this, which we get in Jenkins 2.
                    // Could do this in cleanUpHeap but it is probably not thread-safe.
                    Field f = ASTTransformationVisitor.class.getDeclaredField("compUnit");
                    f.setAccessible(true);
                    f.set(null, null);
                } catch (NoSuchFieldException e) {
                    // assuming that Groovy version is newer
                }
                { // TODO it seems that the call to CpsFlowExecutionTest.register(Object) on a Script1 parameter creates a MetaMethodIndex.Entry.cachedStaticMethod.
                  // In other words any call to a foundational API might leak classes. Why does Groovy need to do this?
                  // Unclear whether this is a problem in a realistic environment; for the moment, suppressing it so the test can run with no SoftReference.
                    MetaClass metaClass = ClassInfo.getClassInfo(CpsFlowExecutionTest.class).getMetaClass();
                    Method clearInvocationCaches = metaClass.getClass().getDeclaredMethod("clearInvocationCaches");
                    clearInvocationCaches.setAccessible(true);
                    clearInvocationCaches.invoke(metaClass);
                }
                for (WeakReference<ClassLoader> loaderRef : LOADERS) {
                    MemoryAssert.assertGC(loaderRef, false);
                }
            }
        });
    }

    @Test public void getCurrentExecutions() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
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
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
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
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                assertStepExecutions(e);
            }
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
        List<String> r = new ArrayList<String>();
        for (StepExecution e : executionsFuture.get()) {
            // TODO should this method be defined in StepContext?
            StepDescriptor d = ((CpsStepContext) e.getContext()).getStepDescriptor();
            assertNotNull(d);
            r.add(d.getFunctionName());
        }
        return r;
    }

    @Issue("JENKINS-25736")
    @Test public void pause() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                story.j.jenkins.setSecurityRealm(story.j.createDummySecurityRealm());
                story.j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                    grant(Jenkins.READ, Item.READ).everywhere().toEveryone().
                    grant(Jenkins.ADMINISTER).everywhere().to("admin").
                    grant(Item.BUILD).onItems(p).to("dev"));
                story.j.jenkins.save();
                p.setDefinition(new CpsFlowDefinition("echo 'before'; semaphore 'one'; echo 'after'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("one/1", b);
                final CpsFlowExecution e = (CpsFlowExecution) b.getExecution();
                assertFalse(e.isPaused());
                JenkinsRule.WebClient wc = story.j.createWebClient();
                WebRequest wrs = new WebRequest(wc.createCrumbedUrl(b.getUrl() + PauseUnpauseAction.URL + "/toggle"), HttpMethod.POST);
                try { // like JenkinsRule.assertFails but taking a WebRequest:
                    fail("should have been rejected but produced: " + wc.getPage(wrs).getWebResponse().getContentAsString());
                } catch (FailingHttpStatusCodeException x) {
                    assertEquals(HttpURLConnection.HTTP_FORBIDDEN, x.getStatusCode());
                }
                wc.login("admin").getPage(wrs);
                assertTrue(e.isPaused());
                story.j.waitForMessage("before", b);
                SemaphoreStep.success("one/1", null);

                // not a very strong way of ensuring that the pause actually happens
                Thread.sleep(1000);
                assertTrue(b.isBuilding());
                assertTrue(e.isPaused());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                assertTrue(b.isBuilding());
                CpsFlowExecution e = (CpsFlowExecution) b.getExecution();
                assertTrue(e.isPaused());
                JenkinsRule.WebClient wc = story.j.createWebClient();
                WebRequest wrs = new WebRequest(wc.createCrumbedUrl(b.getUrl() + PauseUnpauseAction.URL + "/toggle"), HttpMethod.POST);
                wc.login("dev").getPage(wrs);
                assertFalse(e.isPaused());
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                assertFalse(e.isPaused());
            }
        });
    }

    @Issue("JENKINS-32015")
    @Test public void quietDown() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("semaphore 'wait'; echo 'I am done'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
                story.j.jenkins.doQuietDown(true, 0);
                SemaphoreStep.success("wait/1", null);
                ((CpsFlowExecution) b.getExecution()).waitForSuspension();
                assertTrue(b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                story.j.assertLogContains("I am done", story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b)));
            }
        });
    }

    @Issue("JENKINS-26130")
    @Test public void interruptProgramLoad() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("def x = new " + BadThing.class.getCanonicalName() + "(); semaphore 'wait'", true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                Logger LOGGER = Logger.getLogger("org.jenkinsci.plugins.workflow");
                LOGGER.setLevel(Level.FINE);
                Handler handler = new ConsoleHandler();
                handler.setLevel(Level.ALL);
                LOGGER.addHandler(handler);
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getLastBuild();
                assertTrue(b.isBuilding());
                story.j.waitForMessage("Cannot restore BadThing", b);
                b.getExecutor().interrupt();
                story.j.assertBuildStatus(Result.ABORTED, story.j.waitForCompletion(b));
            }
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

    @Test public void trustedShell() {
        trustedShell(true);
    }

    @Test public void trustedShell_control() {
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

    private void trustedShell(final boolean pos) {
        SECRET = false;
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("new foo().attempt()", true));
                WorkflowRun b = p.scheduleBuild2(0).get();
                if (pos) {
                    story.j.assertBuildStatusSuccess(b);
                    assertTrue(SECRET);
                } else {
                    // should have failed with RejectedAccessException trying to touch 'SECRET'
                    story.j.assertBuildStatus(Result.FAILURE, b);
                    story.j.assertLogContains(
                            new RejectedAccessException("staticField",CpsFlowExecutionTest.class.getName()+" SECRET").getMessage(),
                            b);
                    assertFalse(SECRET);
                }
            }
        });
    }

    /**
     * This field shouldn't be visible to regular script.
     */
    public static boolean SECRET;
}
