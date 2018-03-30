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

import com.cloudbees.groovy.cps.CpsTransformer;
import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.html.DomNodeUtil;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import hudson.Functions;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Item;
import hudson.model.Result;

import java.util.logging.Level;

import hudson.security.GlobalMatrixAuthorizationStrategy;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;

import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.*;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class CpsFlowDefinition2Test extends AbstractCpsFlowTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public LoggerRule logging = new LoggerRule();

    /**
     * I should be able to have DSL call into async step and then bring it to the completion.
     */
    @Test public void suspendExecutionAndComeBack() throws Exception {
        CpsFlowDefinition flow = new CpsFlowDefinition(
                "semaphore 'watch'\n" +
                "println 'Yo'");

        // get this going...
        createExecution(flow);
        exec.start();

        SemaphoreStep.waitForStart("watch/1", null);

        assertFalse("Expected the execution to be suspended but it has completed", exec.isComplete());

        FlowExecutionOwner owner = exec.getOwner();
        exec = roundtripXStream(exec);    // poor man's simulation of Jenkins restart
        exec.onLoad(owner);

        // now resume workflow execution
        SemaphoreStep.success("watch/1", null);

        exec.waitForSuspension();
        assertTrue(exec.isComplete());
    }

    /**
     * Verify that we kill endlessly recursive CPS code cleanly.
     */
    @Test
    @Ignore /** Intermittent failures because triggers a longstanding unrelated SandboxResolvingClassloader bug
     resolved in https://github.com/jenkinsci/script-security-plugin/pull/160 */
    public void endlessRecursion() throws Exception {
        Assume.assumeTrue(!Functions.isWindows());  // Sidestep false failures specific to a few Windows build environments.
        String script = "def getThing(){return thing == null}; \n" +
                "node { echo getThing(); } ";
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "recursion");
        job.setDefinition(new CpsFlowDefinition(script, true));

        // Should have failed with error about excessive recursion depth
        WorkflowRun r = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        jenkins.assertLogContains("look for unbounded recursion", r);

        Assert.assertTrue("No queued FlyWeightTask for job should remain after failure", jenkins.jenkins.getQueue().isEmpty());

        for (Computer c : jenkins.jenkins.getComputers()) {
            for (Executor ex : c.getExecutors()) {
                if (ex.isBusy()) {
                    fail(ex.getCurrentExecutable().toString());
                }
            }
        }
    }

    /**
     * Verify that we kill endlessly recursive NonCPS code cleanly and don't leave remnants.
     * This is a bit of extra caution to go along with {@link #endlessRecursion()} to ensure
     *  we don't trigger other forms of failure with the StackOverflowError.
     */
    @Test
    @Ignore  /** Intermittent failures because triggers a longstanding unrelated SandboxResolvingClassloader bug
                 resolved in https://github.com/jenkinsci/script-security-plugin/pull/160 */
    public void endlessRecursionNonCPS() throws Exception {
        Assume.assumeTrue(!Functions.isWindows());  // Sidestep false failures specific to a few Windows build environments.

        String script = "@NonCPS def getThing(){return thing == null}; \n" +
                "node { echo getThing(); } ";
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "recursion");
        job.setDefinition(new CpsFlowDefinition(script, true));

        // Should have failed with error about excessive recursion depth
        WorkflowRun r = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());

        Assert.assertTrue("No queued FlyWeightTask for job should remain after failure", jenkins.jenkins.getQueue().isEmpty());

        for (Computer c : jenkins.jenkins.getComputers()) {
            for (Executor ex : c.getExecutors()) {
                if (ex.isBusy()) {
                    fail(ex.getCurrentExecutable().toString());
                }
            }
        }
    }

    @Test public void configRoundTrip() throws Exception {
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("echo 'whatever'"));
        jenkins.configRoundtrip(job);
    }

    @Issue({"JENKINS-34599", "JENKINS-45629"})
    @Test public void fieldInitializers() throws Exception {
        WorkflowJob p = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("class X {final String val; X(String _val) {val = _val}}; echo(/hello ${new X('world').val}/)", true));
        jenkins.assertLogContains("hello world", jenkins.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("class X {String world = 'world'; String message = 'hello ' + world}; echo(new X().message)", true));
        jenkins.assertLogContains("hello world", jenkins.buildAndAssertSuccess(p));
    }

    @Issue({"JENKINS-42563", "SECURITY-582"})
    @Test
    public void superCallsSandboxed() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("class X extends groovy.json.JsonSlurper {def parse(url) {super.parse(new URL(url))}}; echo(/got ${new X().parse(\"${JENKINS_URL}api/json\")}/)", true));
        WorkflowRun r = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use method groovy.json.JsonSlurper parse java.net.URL", r);
        job.setDefinition(new CpsFlowDefinition("class X extends groovy.json.JsonSlurper {def m(url) {super.parse(new URL(url))}}; echo(/got ${new X().m(\"${JENKINS_URL}api/json\")}/)", true));
        r = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use method groovy.json.JsonSlurper parse java.net.URL", r);
        job.setDefinition(new CpsFlowDefinition("class X extends File {X(String f) {super(f)}}; echo(/got ${new X('x')}/)", true));
        r = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use new java.io.File java.lang.String", r);
    }

    @Test
    public void sandboxInvokerUsed() throws Exception {
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());
        GlobalMatrixAuthorizationStrategy gmas = new GlobalMatrixAuthorizationStrategy();
        // Set up a user with RUN_SCRIPTS and one without..
        gmas.add(Jenkins.RUN_SCRIPTS, "runScriptsUser");
        gmas.add(Jenkins.READ, "runScriptsUser");
        gmas.add(Item.READ, "runScriptsUser");
        gmas.add(Jenkins.READ, "otherUser");
        gmas.add(Item.READ, "otherUser");
        jenkins.jenkins.setAuthorizationStrategy(gmas);
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("[a: 1, b: 2].collectEntries { k, v ->\n" +
                "  Jenkins.getInstance()\n" +
                "  [(v): k]\n" +
                "}\n", true));

        WorkflowRun r = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", r);
        jenkins.assertLogContains("Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance. " + Messages.SandboxContinuable_ScriptApprovalLink(), r);

        JenkinsRule.WebClient wc = jenkins.createWebClient();

        wc.login("runScriptsUser");
        // make sure we see the annotation for the RUN_SCRIPTS user.
        HtmlPage rsp = wc.getPage(r, "console");
        assertEquals(1, DomNodeUtil.selectNodes(rsp, "//A[@href='" + jenkins.contextPath + "/scriptApproval']").size());

        // make sure raw console output doesn't include the garbage and has the right message.
        TextPage raw = (TextPage)wc.goTo(r.getUrl()+"consoleText","text/plain");
        assertThat(raw.getContent(), containsString(" getInstance. " + Messages.SandboxContinuable_ScriptApprovalLink()));

        wc.login("otherUser");
        // make sure we don't see the link for the other user.
        HtmlPage rsp2 = wc.getPage(r, "console");
        assertEquals(0, DomNodeUtil.selectNodes(rsp2, "//A[@href='" + jenkins.contextPath + "/scriptApproval']").size());

        // make sure raw console output doesn't include the garbage and has the right message.
        TextPage raw2 = (TextPage)wc.goTo(r.getUrl()+"consoleText","text/plain");
        assertThat(raw2.getContent(), containsString(" getInstance. " + Messages.SandboxContinuable_ScriptApprovalLink()));
    }

    @Issue("SECURITY-551")
    @Test
    public void constructorSandbox() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("class X {X() {Jenkins.instance.systemMessage = 'pwned'}}; new X()", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        assertNull(jenkins.jenkins.getSystemMessage());
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
    }

    @Issue("SECURITY-551")
    @Test
    public void fieldInitializerSandbox() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("class X {def x = {Jenkins.instance.systemMessage = 'pwned'}()}; new X()", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        assertNull(jenkins.jenkins.getSystemMessage());
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
    }

    @Issue("SECURITY-551")
    @Test
    public void initializerSandbox() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("class X {{Jenkins.instance.systemMessage = 'pwned'}}; new X()", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        assertNull(jenkins.jenkins.getSystemMessage());
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
    }

    @Test
    public void staticInitializerSandbox() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("class X {static {Jenkins.instance.systemMessage = 'pwned'}}; new X()", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        assertNull(jenkins.jenkins.getSystemMessage());
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
    }

    @Test
    public void traitsSandbox() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("trait T {void m() {Jenkins.instance.systemMessage = 'pwned'}}; class X implements T {}; new X().m()", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        assertNull(jenkins.jenkins.getSystemMessage());
        jenkins.assertBuildStatus(Result.FAILURE, b);
        /* TODO instead it fails in some cryptic spot while trying to translate the body of the trait
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
        */
        job.setDefinition(new CpsFlowDefinition("trait T {void m() {Jenkins.instance.systemMessage = 'pwned'}}; T t = new TreeSet() as T; t.m()", true));
        b = job.scheduleBuild2(0).get();
        assertNull(jenkins.jenkins.getSystemMessage());
        jenkins.assertBuildStatus(Result.FAILURE, b);
        // TODO this one fails with a NullPointerException
    }

    @Issue("SECURITY-566")
    @Test public void typeCoercion() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("interface I {Object getInstance()}; println((Jenkins as I).instance)", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        assertNull(jenkins.jenkins.getSystemMessage());
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
        // Not really the same but just checking:
        job.setDefinition(new CpsFlowDefinition("interface I {Object getInstance()}; I i = {Jenkins.instance}; println(i.instance)", true));
        b = job.scheduleBuild2(0).get();
        assertNull(jenkins.jenkins.getSystemMessage());
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
    }

    @Issue("SECURITY-580")
    @Test public void positionalConstructors() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob p = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        // Control cases:
        p.setDefinition(new CpsFlowDefinition("def u = ['http://nowhere.net/'] as URL; echo(/$u/)", true));
        jenkins.buildAndAssertSuccess(p);
        p.setDefinition(new CpsFlowDefinition("URL u = ['http://nowhere.net/']; echo(/$u/)", true));
        jenkins.buildAndAssertSuccess(p);
        p.setDefinition(new CpsFlowDefinition("def f = new File('/tmp'); echo(/$f/)", true));
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use new java.io.File java.lang.String", jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
        // Test cases:
        p.setDefinition(new CpsFlowDefinition("def f = ['/tmp'] as File; echo(/$f/)", true));
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use new java.io.File java.lang.String", jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
        p.setDefinition(new CpsFlowDefinition("File f = ['/tmp']; echo(/$f/)", true));
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use new java.io.File java.lang.String", jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
    }

    @Issue("SECURITY-567")
    @Test public void methodPointers() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("println((Jenkins.&getInstance)())", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
    }

    @Issue("JENKINS-38052")
    @Test
    public void curriedClosuresInParallel() throws Exception {
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("def example_c = { input -> node { echo \"$input\" } }\n" +
                "def map = [:]\n" +
                "map['spam'] = example_c.curry('spam')\n" +
                "map['eggs'] = example_c.curry('eggs')\n" +
                "parallel map\n", true));
        WorkflowRun b = jenkins.buildAndAssertSuccess(job);
        jenkins.assertLogContains("[spam] spam", b);
        jenkins.assertLogContains("[eggs] eggs", b);
    }

    @Issue("JENKINS-27916")
    @Test
    public void gStringInMapKey() throws Exception {
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("def s1 = \"first-${env.BUILD_NUMBER}\"\n" +
                "def s2 = \"second-${env.BUILD_NUMBER}\"\n" +
                "def m = [(s1): 'first-key',\n" +
                "  \"${s2}\": 'second-key',\n" +
                "  \"third-${env.BUILD_NUMBER}\": 'third-key']\n" +
                "m.each { k, v -> echo \"${k}:${v}\" }\n", true));

        WorkflowRun b = jenkins.buildAndAssertSuccess(job);
        jenkins.assertLogContains("first-1:first-key", b);
        jenkins.assertLogContains("second-1:second-key", b);
        jenkins.assertLogContains("third-1:third-key", b);
    }

    @Issue("JENKINS-41248")
    @Test
    public void explicitSetter() throws Exception {
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("class Foo {\n" +
                "    private int a\n" +
                "    void setA(int a) {\n" +
                "        this.a = a\n" +
                "    }\n" +
                "    String getA() {\n" +
                "        return a\n" +
                "    }\n" +
                "}\n" +
                "Foo foo = new Foo()\n" +
                "foo.setA(10)\n" +
                "echo \"a is ${foo.getA()}\"", true));
        WorkflowRun b = jenkins.buildAndAssertSuccess(job);

        jenkins.assertLogContains("a is 10", b);
    }

    @Issue("JENKINS-28321")
    @Test
    public void whitelistedMethodPointer() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("def foo = 'lowercase'\n" +
                "def bar = foo.&toUpperCase\n" +
                "echo bar.call()\n", true));

        WorkflowRun b = jenkins.buildAndAssertSuccess(job);

        jenkins.assertLogContains("LOWERCASE", b);
    }

    @Issue("JENKINS-46391")
    @Test
    public void tildePattern() throws Exception {
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("def f = ~/f.*/; f.matcher('foo').matches()", true));
        jenkins.buildAndAssertSuccess(job);
    }

    @Issue("JENKINS-46088")
    @Test
    public void matcherTypeAssignment() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob p = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("@NonCPS\n" +
                "def nonCPSMatcherMethod(String x) {\n" +
                "  java.util.regex.Matcher m = x =~ /bla/\n" +
                "  return m.matches()\n" +
                "}\n" +
                "def cpsMatcherMethod(String x) {\n" +
                "  java.util.regex.Matcher m = x =~ /bla/\n" +
                "  return m.matches()\n" +
                "}\n" +
                "assert !nonCPSMatcherMethod('foo')\n" +
                "assert !cpsMatcherMethod('foo')\n", true));

        jenkins.buildAndAssertSuccess(p);
    }

    @Issue("JENKINS-46088")
    @Test
    public void rhsOfDeclarationTransformedInNonCPS() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("@NonCPS\n" +
                "def willFail() {\n" +
                "  jenkins.model.Jenkins x = jenkins.model.Jenkins.getInstance()\n" +
                "}\n" +
                "willFail()\n", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
    }

    @Issue("JENKINS-46088")
    @Test
    public void rhsOfDeclarationSandboxedInCPS() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("jenkins.model.Jenkins x = jenkins.model.Jenkins.getInstance()\n", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
    }

    @Issue("JENKINS-47064")
    @Test
    public void booleanClosureWrapperFromDGM() throws Exception {
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("assert ['a', 'b'].every { sleep 1; return it != null }\n", true));
        jenkins.buildAndAssertSuccess(job);
    }

    @Issue("JENKINS-48501")
    @Test
    public void variableDecl() throws Exception {
        WorkflowJob p = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("String foo", true));
        jenkins.buildAndAssertSuccess(p);
    }

    @Issue("JENKINS-45575")
    @Test
    public void multipleAssignmentInSandbox() throws Exception {
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("def (a, b) = ['first', 'second']\n" +
                "def c, d\n" +
                "(c, d) = ['third', 'fourth']\n" +
                "assert a+b+c+d == 'firstsecondthirdfourth'\n", true));
        jenkins.buildAndAssertSuccess(job);
    }

    @Issue("JENKINS-45575")
    @Test
    public void multipleAssignmentOutsideSandbox() throws Exception {
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("def (a, b) = ['first', 'second']\n" +
                "def c, d\n" +
                "(c, d) = ['third', 'fourth']\n" +
                "assert a+b+c+d == 'firstsecondthirdfourth'\n", false));
        jenkins.buildAndAssertSuccess(job);
    }

    @Issue("JENKINS-49679")
    @Test
    public void multipleAssignmentFunctionCalledOnce() throws Exception {
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("alreadyRun = false\n" +
                "def getAandB() {\n" +
                "  if (!alreadyRun) {\n" +
                "    alreadyRun = true\n" +
                "    return ['first', 'second']\n" +
                "  } else {\n" +
                "    return ['bad', 'worse']\n" +
                "  }\n" +
                "}\n" +
                "def (a, b) = getAandB()\n" +
                "def c, d\n" +
                "(c, d) = ['third', 'fourth']\n" +
                "assert a+b+c+d == 'firstsecondthirdfourth'\n", true));
        jenkins.buildAndAssertSuccess(job);
    }

    @Issue("JENKINS-45982")
    @Test
    public void transformedSuperClass() throws Exception {
        WorkflowJob job = jenkins.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition("class Foo {\n" +
                "    public String other() {\n" +
                "        return 'base'\n" +
                "    }\n" +
                "}\n" +
                "class Bar extends Foo {\n" +
                "    public String other() {\n" +
                "        return 'y'+super.other()\n" +
                "    }\n" +
                "}\n" +
                "String output = new Bar().other()\n" +
                "echo 'OUTPUT: ' + output\n" +
                "assert output == 'ybase'\n", true));
        WorkflowRun r = jenkins.buildAndAssertSuccess(job);
        jenkins.assertLogContains("OUTPUT: ybase", r);
    }
}
