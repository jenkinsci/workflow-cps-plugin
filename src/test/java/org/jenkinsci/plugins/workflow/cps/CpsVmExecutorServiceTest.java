/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.rules.ErrorCollector;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class CpsVmExecutorServiceTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public ErrorCollector errors = new ErrorCollector();

    @Test public void contextClassLoader() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo(/yes I can load ${Thread.currentThread().contextClassLoader.loadClass(getClass().name)}/)", false));
        r.buildAndAssertSuccess(p);
    }

    @Issue({"JENKINS-31314", "JENKINS-27306", "JENKINS-26313"})
    @Test public void wrongCatcher() throws Exception {
        boolean origFailOnMismatch = CpsVmExecutorService.FAIL_ON_MISMATCH;
        CpsVmExecutorService.FAIL_ON_MISMATCH = false;
        try {
            WorkflowJob p = r.createProject(WorkflowJob.class, "p");
            errors.checkSucceeds(() -> {
                p.setDefinition(new CpsFlowDefinition("def ok() {sleep 1}; @NonCPS def bad() {for (int i = 0; i < 10; i++) {sleep 1}; assert false : 'never gets here'}; node {ok(); bad()}", true));
                r.assertLogContains(CpsVmExecutorService.mismatchMessage("WorkflowScript", "bad", null, "sleep"), r.buildAndAssertSuccess(p));
                return null;
            });
            errors.checkSucceeds(() -> {
                p.setDefinition(new CpsFlowDefinition("def l = [3, 2, 1]; println(/oops got ${l.sort {x, y -> x - y}}/)", true));
                WorkflowRun b = r.buildAndAssertSuccess(p);
                r.assertLogContains("oops got -1", b);
                r.assertLogContains(CpsVmExecutorService.mismatchMessage("java.util.ArrayList", "sort", "org.jenkinsci.plugins.workflow.cps.CpsClosure2", "call"), b);
                return null;
            });
            errors.checkSucceeds(() -> {
                p.setDefinition(new CpsFlowDefinition("node {[1, 2, 3].each {x -> sleep 1; echo(/no problem got $x/)}}", true));
                WorkflowRun b = r.buildAndAssertSuccess(p);
                r.assertLogContains("no problem got 3", b);
                r.assertLogNotContains("expected to call", b);
                return null;
            });
            errors.checkSucceeds(() -> {
                p.setDefinition(new CpsFlowDefinition("class C {@Override String toString() {'never used'}}; def gstring = /embedding ${new C()}/; echo(/oops got $gstring/)", true));
                WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0)); // JENKINS-27306: No such constructor found: new org.codehaus.groovy.runtime.GStringImpl java.lang.String java.lang.String[]
                r.assertLogContains(CpsVmExecutorService.mismatchMessage("org.codehaus.groovy.runtime.ScriptBytecodeAdapter", "asType", "C", "toString"), b);
                return null;
            });
            errors.checkSucceeds(() -> {
                p.setDefinition(new CpsFlowDefinition("echo(/see what ${-> 'this'} does/)", true));
                WorkflowRun b = r.buildAndAssertSuccess(p);
                r.assertLogContains(CpsVmExecutorService.mismatchMessage("WorkflowScript", "echo", "org.jenkinsci.plugins.workflow.cps.CpsClosure2", "call"), b);
                r.assertLogNotContains("see what", b);
                return null;
            });
            errors.checkSucceeds(() -> {
                p.setDefinition(new CpsFlowDefinition("node {echo(/see what ${-> 'this'} does/)}", true));
                WorkflowRun b = r.buildAndAssertSuccess(p);
                r.assertLogContains(CpsVmExecutorService.mismatchMessage("WorkflowScript", "echo", "org.jenkinsci.plugins.workflow.cps.CpsClosure2", "call"), b);
                r.assertLogNotContains("see what", b);
                return null;
            });
            errors.checkSucceeds(() -> {
                p.setDefinition(new CpsFlowDefinition(
                    "@NonCPS def shouldBomb() {\n" +
                    "  def text = ''\n" +
                    "  ['a', 'b', 'c'].each {it -> writeFile file: it, text: it; text += it}\n" +
                    "  text\n" +
                    "}\n" +
                    "node {\n" +
                    "  echo shouldBomb()\n" +
                    "}\n", true));
                r.assertLogContains(CpsVmExecutorService.mismatchMessage("WorkflowScript", "shouldBomb", null, "writeFile"), r.buildAndAssertSuccess(p));
                return null;
            });
            errors.checkSucceeds(() -> {
                p.setDefinition(new CpsFlowDefinition("@NonCPS def bad() {polygon(17) {}}; bad()", true));
                WorkflowRun b = r.buildAndAssertSuccess(p);
                r.assertLogContains("wrapping in a 17-gon", b);
                r.assertLogContains(CpsVmExecutorService.mismatchMessage("WorkflowScript", "bad", null, "polygon"), b);
                return null;
            });
            errors.checkSucceeds(() -> {
                p.setDefinition(new CpsFlowDefinition("class C {C(script) {script.sleep(1)}}; new C(this)", true));
                WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
                r.assertLogContains(CpsVmExecutorService.mismatchMessage("C", "<init>", null, "sleep"), b);
                return null;
            });
        } finally {
            CpsVmExecutorService.FAIL_ON_MISMATCH = origFailOnMismatch;
        }
    }

    @Issue("JENKINS-58501")
    @Ignore
    @Test public void mismatchMetaProgrammingFalsePositives() throws Exception {
        boolean origFailOnMismatch = CpsVmExecutorService.FAIL_ON_MISMATCH;
        CpsVmExecutorService.FAIL_ON_MISMATCH = false;
        try {
            WorkflowJob p = r.createProject(WorkflowJob.class, "p");
            errors.checkSucceeds(() -> {
                p.setDefinition(new CpsFlowDefinition(
                    "import org.codehaus.groovy.runtime.InvokerHelper \n" + 
                    "c = { println 'doing a thing' } \n" +
                    "InvokerHelper.getMetaClass(c).invokeMethod(c, 'call', null)", false));
                WorkflowRun b = r.buildAndAssertSuccess(p);
                r.assertLogNotContains("MetaClassImpl", b);
                return null;
            });
            errors.checkSucceeds(() -> {
                p.setDefinition(new CpsFlowDefinition(
                    "import org.codehaus.groovy.runtime.InvokerHelper \n" + 
                    "c = { println 'doing a thing' } \n" +
                    "c.getMetaClass().someField = 'r' \n" + 
                    "InvokerHelper.getMetaClass(c).invokeMethod(c, 'call', null)", false));
                WorkflowRun b = r.buildAndAssertSuccess(p);
                r.assertLogNotContains("ExpandoMetaClass", b);
                return null;
            });
            errors.checkSucceeds(() -> {
                p.setDefinition(new CpsFlowDefinition(
                    "import org.codehaus.groovy.runtime.InvokerHelper \n" + 
                    "class Example{ \n" +
                        "def script \n" + 
                        "Example(script){ this.script = script } \n" + 
                        "def methodMissing(String methodName, args){ \n" + 
                            "return InvokerHelper.getMetaClass(this).invokeMethod(this, 'exists', null) \n" + 
                        "} \n" +
                        "def exists(){ script.println 'doing a thing' } \n" +
                    "} \n" + 
                    "def e = new Example(this) \n" +
                    "e.doSomething()", false));
                WorkflowRun b = r.buildAndAssertSuccess(p);
                r.assertLogNotContains("methodMissing", b);
                return null;
            });
        } finally {
            CpsVmExecutorService.FAIL_ON_MISMATCH = origFailOnMismatch;
        }
    }

}
