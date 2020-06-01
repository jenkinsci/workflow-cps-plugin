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
import hudson.Functions;
import hudson.model.Computer;
import hudson.model.Executor;
import hudson.model.Result;

import java.util.logging.Level;

import org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;

import static org.junit.Assert.*;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import static org.hamcrest.Matchers.instanceOf;

public class CpsFlowDefinition2Test {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @ClassRule public static JenkinsRule jenkins = new JenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule();
    @Rule public ErrorCollector errors = new ErrorCollector();

    /**
     * Verify that we kill endlessly recursive CPS code cleanly.
     */
    @Test
    public void endlessRecursion() throws Exception {
        Assume.assumeTrue(!Functions.isWindows());  // Sidestep false failures specific to a few Windows build environments.
        String script = "def getThing(){return thing == null}; \n" +
                "node { echo getThing(); } ";
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
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
    public void endlessRecursionNonCPS() throws Exception {
        Assume.assumeTrue(!Functions.isWindows());  // Sidestep false failures specific to a few Windows build environments.

        String script = "@NonCPS def getThing(){return thing == null}; \n" +
                "node { echo getThing(); } ";
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
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
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("echo 'whatever'", false));
        jenkins.configRoundtrip(job);
    }

    @Issue({"JENKINS-34599", "JENKINS-45629"})
    @Test public void fieldInitializers() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("class X {final String val; X(String _val) {val = _val}}; echo(/hello ${new X('world').val}/)", true));
        jenkins.assertLogContains("hello world", jenkins.buildAndAssertSuccess(p));
        p.setDefinition(new CpsFlowDefinition("class X {String world = 'world'; String message = 'hello ' + world}; echo(new X().message)", true));
        jenkins.assertLogContains("hello world", jenkins.buildAndAssertSuccess(p));
    }

    @Issue({"JENKINS-42563", "SECURITY-582"})
    @Test
    public void superCallsSandboxed() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
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
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("[a: 1, b: 2].collectEntries { k, v ->\n" +
                "  Jenkins.getInstance()\n" +
                "  [(v): k]\n" +
                "}\n", true));

        WorkflowRun r = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0).get());
        assertThat(r.getExecution().getCauseOfFailure(), instanceOf(RejectedAccessException.class));
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", r);
        jenkins.assertLogContains("Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance. " + org.jenkinsci.plugins.scriptsecurity.scripts.Messages.ScriptApprovalNote_message(), r);
    }

    @Issue("SECURITY-551")
    @Test
    public void constructorSandbox() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
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
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
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
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("class X {{Jenkins.instance.systemMessage = 'pwned'}}; new X()", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        assertNull(jenkins.jenkins.getSystemMessage());
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
    }

    @Test
    public void staticInitializerSandbox() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("class X {static {Jenkins.instance.systemMessage = 'pwned'}}; new X()", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        assertNull(jenkins.jenkins.getSystemMessage());
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
    }

    @Test
    public void traitsSandbox() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
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
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        errors.checkSucceeds(() -> {
            job.setDefinition(new CpsFlowDefinition("interface I {Object getInstance()}; println((Jenkins as I).instance)", true));
            WorkflowRun b = job.scheduleBuild2(0).get();
            assertNull(jenkins.jenkins.getSystemMessage());
            jenkins.assertBuildStatus(Result.FAILURE, b);
            jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
            return null;
        });
        // Not really the same but just checking:
        errors.checkSucceeds(() -> {
            job.setDefinition(new CpsFlowDefinition("interface I {Object getInstance()}; I i = {Jenkins.instance}; println(i.instance)", true));
            WorkflowRun b = job.scheduleBuild2(0).get();
            assertNull(jenkins.jenkins.getSystemMessage());
            jenkins.assertBuildStatus(Result.FAILURE, b);
            jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
            return null;
        });
        // Some safe idioms:
        errors.checkSucceeds(() -> {
            job.setDefinition(new CpsFlowDefinition("def x = (double) Math.max(2, 3); echo(/max is $x/)", true));
            jenkins.assertLogContains("max is 3", jenkins.buildAndAssertSuccess(job));
            return null;
        });
        errors.checkSucceeds(() -> {
            job.setDefinition(new CpsFlowDefinition("def x = Math.max(2, 3) as double; echo(/max is $x/)", true));
            jenkins.assertLogContains("max is 3", jenkins.buildAndAssertSuccess(job));
            return null;
        });
        errors.checkSucceeds(() -> {
            job.setDefinition(new CpsFlowDefinition("double x = Math.max(2, 3); echo(/max is $x/)", true));
            jenkins.assertLogContains("max is 3", jenkins.buildAndAssertSuccess(job));
            return null;
        });
    }

    @Issue({"SECURITY-580", "SECURITY-1353"})
    @Test public void positionalConstructors() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        // Control cases:
        errors.checkSucceeds(() -> {
            p.setDefinition(new CpsFlowDefinition("def u = ['http://nowhere.net/'] as URL; echo(/$u/)", true));
            jenkins.buildAndAssertSuccess(p);
            return null;
        });
        errors.checkSucceeds(() -> {
            p.setDefinition(new CpsFlowDefinition("URL u = ['http://nowhere.net/']; echo(/$u/)", true));
            jenkins.buildAndAssertSuccess(p);
            return null;
        });
        errors.checkSucceeds(() -> {
            p.setDefinition(new CpsFlowDefinition("def f = new File('/tmp'); echo(/$f/)", true));
            jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use new java.io.File java.lang.String", jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
            return null;
        });
        // Test cases:
        errors.checkSucceeds(() -> {
            p.setDefinition(new CpsFlowDefinition("def f = ['/tmp'] as File; echo(/$f/)", true));
            jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use new java.io.File java.lang.String", jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
            return null;
        });
        errors.checkSucceeds(() -> {
            p.setDefinition(new CpsFlowDefinition("File f = ['/tmp']; echo(/$f/)", true));
            jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use new java.io.File java.lang.String", jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
            return null;
        });
        errors.checkSucceeds(() -> {
            p.setDefinition(new CpsFlowDefinition("def f = org.codehaus.groovy.runtime.ScriptBytecodeAdapter.asType(['/tmp'], File); echo(/$f/)", true));
            jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use new java.io.File java.lang.String", jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
            return null;
        });
        errors.checkSucceeds(() -> {
            p.setDefinition(new CpsFlowDefinition("def f = org.codehaus.groovy.runtime.ScriptBytecodeAdapter.castToType(['/tmp'], File); echo(/$f/)", true));
            jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod org.codehaus.groovy.runtime.ScriptBytecodeAdapter castToType java.lang.Object java.lang.Class", jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
            return null;
        });
        errors.checkSucceeds(() -> {
            p.setDefinition(new CpsFlowDefinition("def f = org.kohsuke.groovy.sandbox.impl.Checker.checkedCast(File, ['/tmp'], true, false, false); echo(/$f/)", true));
            jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use new java.io.File java.lang.String", jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)));
            return null;
        });
    }

    @Issue("SECURITY-567")
    @Test public void methodPointers() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("println((Jenkins.&getInstance)())", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
    }

    @Issue("JENKINS-38052")
    @Test
    public void curriedClosuresInParallel() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("def example_c = { input -> node { echo \"ate $input\" } }\n" +
                "def map = [:]\n" +
                "map['spam'] = example_c.curry('spam')\n" +
                "map['eggs'] = example_c.curry('eggs')\n" +
                "parallel map\n", true));
        WorkflowRun b = jenkins.buildAndAssertSuccess(job);
        jenkins.assertLogContains("ate spam", b);
        jenkins.assertLogContains("ate eggs", b);
    }

    @Issue("JENKINS-27916")
    @Test
    public void gStringInMapKey() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
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
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("def foo = 'lowercase'\n" +
                "def bar = foo.&toUpperCase\n" +
                "echo bar.call()\n", true));

        WorkflowRun b = jenkins.buildAndAssertSuccess(job);

        jenkins.assertLogContains("LOWERCASE", b);
    }

    @Issue("JENKINS-46391")
    @Test
    public void tildePattern() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("def f = ~/f.*/; f.matcher('foo').matches()", true));
        jenkins.buildAndAssertSuccess(job);
    }

    @Issue("JENKINS-46088")
    @Test
    public void matcherTypeAssignment() throws Exception {
        logging.record(CpsTransformer.class, Level.FINEST);
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
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
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
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
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("jenkins.model.Jenkins x = jenkins.model.Jenkins.getInstance()\n", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
    }

    @Issue("JENKINS-47064")
    @Test
    public void booleanClosureWrapperFromDGM() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("assert ['a', 'b'].every { sleep 1; return it != null }\n", true));
        jenkins.buildAndAssertSuccess(job);
    }

    @Issue("JENKINS-48501")
    @Test
    public void variableDecl() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("String foo", true));
        jenkins.buildAndAssertSuccess(p);
    }

    @Issue("JENKINS-45575")
    @Test
    public void multipleAssignmentInSandbox() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("def (a, b) = ['first', 'second']\n" +
                "def c, d\n" +
                "(c, d) = ['third', 'fourth']\n" +
                "assert a+b+c+d == 'firstsecondthirdfourth'\n", true));
        jenkins.buildAndAssertSuccess(job);
    }

    @Issue("JENKINS-45575")
    @Test
    public void multipleAssignmentOutsideSandbox() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition("def (a, b) = ['first', 'second']\n" +
                "def c, d\n" +
                "(c, d) = ['third', 'fourth']\n" +
                "assert a+b+c+d == 'firstsecondthirdfourth'\n", false));
        jenkins.buildAndAssertSuccess(job);
    }

    @Issue("JENKINS-49679")
    @Test
    public void multipleAssignmentFunctionCalledOnce() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
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
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
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

    @Issue("SECURITY-1186")
    @Test
    public void finalizer() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("class Foo {\n" +
                "    @Override public void finalize() {\n" +
                "    }\n" +
                "}\n" +
                "echo 'Should never get here'", true));
        WorkflowRun b = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("Object.finalize()", b);
        jenkins.assertLogNotContains("Should never get here", b);
    }

    @Issue("SECURITY-266")
    @Test
    public void sandboxRejectsASTTransforms() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("import groovy.transform.*\n" +
                "import jenkins.model.Jenkins\n" +
                "import org.jenkinsci.plugins.workflow.job.WorkflowJob\n" +
                "@ASTTest(value={ assert Jenkins.get().createProject(WorkflowJob.class, \"should-not-exist\") })\n" +
                "@Field int x\n" +
                "echo 'hello'\n", true));
        WorkflowRun b = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("Annotation ASTTest cannot be used in the sandbox", b);

        assertNull(jenkins.jenkins.getItem("should-not-exist"));
    }

    @Issue("SECURITY-1336")
    @Test
    public void blockConstructorInvocationAtRuntime() throws Exception {
        WorkflowJob job = jenkins.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition(
            "class DoNotRunConstructor extends org.jenkinsci.plugins.workflow.cps.CpsScript {\n" +
            "  DoNotRunConstructor() {\n" +
            "    assert jenkins.model.Jenkins.instance.createProject(hudson.model.FreeStyleProject, 'should-not-exist')\n" +
            "  }\n" +
            "  Object run() {null}\n" +
            "}\n", true));
        WorkflowRun b = job.scheduleBuild2(0).get();
        assertNull(jenkins.jenkins.getItem("should-not-exist"));
        jenkins.assertBuildStatus(Result.FAILURE, b);
        jenkins.assertLogContains("staticMethod jenkins.model.Jenkins getInstance", b);
    }

    @Issue("JENKINS-56682")
    @Test
    public void scriptInitializersAtFieldSyntax() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "import groovy.transform.Field\n" +
                "@Field static int foo = 1\n" +
                "@Field int bar = foo + 1\n" +
                "@Field int baz = bar + 1\n" +
                "echo(/baz is ${baz}/)", true));
        WorkflowRun b = jenkins.buildAndAssertSuccess(p);
        jenkins.assertLogContains("baz is 3", b);
    }

    @Issue("JENKINS-56682")
    @Test
    public void scriptInitializersClassSyntax() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "class MyScript extends org.jenkinsci.plugins.workflow.cps.CpsScript {\n" +
                "  { MyScript.foo++ }\n" + // The instance initializer seems to be context sensitive, if placed below the field it is treated as a closure...
                "  static { MyScript.foo++ }\n" +
                "  static int foo = 0\n" +
                "  def run() {\n" +
                "    echo(/MyScript.foo is ${MyScript.foo}/)\n " +
                "  }\n" +
                "}\n", true));
        WorkflowRun b = jenkins.buildAndAssertSuccess(p);
        jenkins.assertLogContains("MyScript.foo is 2", b);
    }

    @Issue("JENKINS-56682")
    @Test
    public void scriptInitializerCallsCpsTransformedMethod() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "class MyScript extends org.jenkinsci.plugins.workflow.cps.CpsScript {\n" +
                "  static { bar() }\n" +
                "  static int foo = 0\n" +
                "  static def bar() { MyScript.foo += 1 }\n" +
                "  def run() {\n" +
                "    echo(/MyScript.foo is ${MyScript.foo}/)\n " +
                "  }\n" +
                "}\n", true));
        WorkflowRun b = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("CpsCallableInvocation{methodName=bar,", b);
    }

    @Issue("SECURITY-1465")
    @Test public void blockLhsInMethodPointerExpression() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "({" +
                "  System.getProperties()\n" +
                "  1" +
                "}().&toString)()", true));
        WorkflowRun b = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("staticMethod java.lang.System getProperties", b);
    }

    @Issue("SECURITY-1465")
    @Test public void blockRhsInMethodPointerExpression() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "1.&(System.getProperty('sandboxTransformsMethodPointerRhs'))()", true));
        WorkflowRun b = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("staticMethod java.lang.System getProperty java.lang.String", b);
    }

    @Issue("SECURITY-1465")
    @Test public void blockCastingUnsafeUserDefinedImplementationsOfCollection() throws Exception {
        // See additional info on this test case in `SandboxTransformerTest.sandboxWillNotCastNonStandardCollections()` over in groovy-sandbox.
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "import groovy.transform.Field\n" +
                "@Field def i = 0\n" +
                "@NonCPS def unsafe() {\n" + // Using an @NonCPS method instead of a closure to avoid a CpsCallableInvocation being thrown out of Checker.preCheckedCast() when it invokes a method on the proxied Collection.
                "  if(i) {\n" +
                "    return ['secret.txt'] as Object[]\n" +
                "  } else {\n" +
                "    i = 1\n" +
                "    return null\n" +
                "  }\n" +
                "}\n" +
                "((this.&unsafe as Collection) as File) as Object[]", true));
        WorkflowRun b = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        // Before the security fix, fails with FileNotFoundException, bypassing the sandbox!
        jenkins.assertLogContains("Casting non-standard Collections to a type via constructor is not supported", b);
    }

    @Issue("SECURITY-1465")
    @Test public void blockCastingSafeUserDefinedImplementationsOfCollection() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "@NonCPS def safe() {\n" + // Using an @NonCPS method instead of a closure to avoid a CpsCallableInvocation being thrown out of Checker.preCheckedCast() when it invokes a method on the proxied Collection.
                "  return ['secret.txt'] as Object[]\n" +
                "}\n" +
                "(this.&safe as Collection) as File", true));
        WorkflowRun b = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        // Before the security fix, fails because `new File(String)` is not whitelisted, so not a problem, but we have
        // no good way to distinguish this case from the one in blockCastingUnsafeUserDefinedImplementationsOfCollection.
        jenkins.assertLogContains("Casting non-standard Collections to a type via constructor is not supported", b);
    }

    @Issue("SECURITY-1465")
    @Test public void blockEnumConstants() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("jenkins.YesNoMaybe.MAYBE", true));
        WorkflowRun b1 = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("staticField jenkins.YesNoMaybe MAYBE", b1);

        p.setDefinition(new CpsFlowDefinition("jenkins.YesNoMaybe.class as Object[]", true));
        WorkflowRun b2 = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("staticField jenkins.YesNoMaybe YES", b2);
    }

    @Issue("SECURITY-1538")
    @Test public void blockMethodNameInMethodCalls() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("1.({ Jenkins.getInstance(); 'toString' }())()", true));
        WorkflowRun b1 = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("staticMethod jenkins.model.Jenkins getInstance", b1);
        // @NonCPS equivalent
        p.setDefinition(new CpsFlowDefinition(
                "def @NonCPS method() {\n" +
                "  1.({ Jenkins.getInstance(); 'toString' }())()\n" +
                "}\n" +
                "method()", true));
        WorkflowRun b2 = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("staticMethod jenkins.model.Jenkins getInstance", b2);
    }

    @Issue("SECURITY-1538")
    @Test public void blockPropertyNameInAssignment() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "class Test { def x = 0 }\n" +
                "def t = new Test()\n" +
                "t.({ Jenkins.getInstance(); 'x' }()) = 1\n", true));
        WorkflowRun b1 = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("staticMethod jenkins.model.Jenkins getInstance", b1);
        // @NonCPS equivalent
        p.setDefinition(new CpsFlowDefinition(
                "class Test { def x = 0 }\n" +
                "def @NonCPS method() {\n" +
                "  def t = new Test()\n" +
                "  t.({ Jenkins.getInstance(); 'x' }()) = 1\n" +
                "}\n" +
                "method()", true));
        WorkflowRun b2 = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("staticMethod jenkins.model.Jenkins getInstance", b2);
    }

    @Issue("SECURITY-1538")
    @Test public void blockPropertyNameInPrefixPostfixExpressions() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "class Test { def x = 0 }\n" +
                "def t = new Test()\n" +
                "t.({ Jenkins.getInstance(); 'x' }())++\n", true));
        WorkflowRun b1 = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("staticMethod jenkins.model.Jenkins getInstance", b1);
        // @NonCPS equivalent
        p.setDefinition(new CpsFlowDefinition(
                "class Test { def x = 0 }\n" +
                "def @NonCPS method() {\n" +
                "  def t = new Test()\n" +
                "  t.({ Jenkins.getInstance(); 'x' }())++\n" +
                "}\n" +
                "method()", true));
        WorkflowRun b2 = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("staticMethod jenkins.model.Jenkins getInstance", b2);
    }

    @Issue("SECURITY-1538")
    @Test public void blockSubexpressionsInPrefixPostfixExpressions() throws Exception {
        // Prefix
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("++({ Jenkins.getInstance(); 1 }())", true));
        WorkflowRun b1 = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("MissingMethodException: No signature of method: com.cloudbees.groovy.cps.Builder.prefixInc", b1);
        // @NonCPS prefix
        p.setDefinition(new CpsFlowDefinition("def @NonCPS method() { ++({ Jenkins.getInstance(); 1 }()) }; method()", true));
        WorkflowRun b2 = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("staticMethod jenkins.model.Jenkins getInstance", b2);
        // Postfix
        p.setDefinition(new CpsFlowDefinition("({ Jenkins.getInstance(); 1 }())++", true));
        WorkflowRun b3 = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("MissingMethodException: No signature of method: com.cloudbees.groovy.cps.Builder.postfixInc", b3);
        // @NonCPS postfix
        p.setDefinition(new CpsFlowDefinition("def @NonCPS method() { ({ Jenkins.getInstance(); 1 }())++ }; method()", true));
        WorkflowRun b4 = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("staticMethod jenkins.model.Jenkins getInstance", b4);
    }

    @Issue("SECURITY-1710")
    @Test public void blockInitialExpressionsForParamsInCpsTransformedMethods() throws Exception {
        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("def m(p = Jenkins.getInstance()) { true }; m()", true));
        WorkflowRun b = jenkins.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        jenkins.assertLogContains("staticMethod jenkins.model.Jenkins getInstance", b);
    }

}
