/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps.persistence;

import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.RestartableJenkinsRule;

@Issue("JENKINS-27421")
public class IteratorHackTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule rr = new RestartableJenkinsRule();

    @Test public void listIterator() {
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = rr.j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "def arr = []; arr += 'one'; arr += 'two'\n" +
                    "for (int i = 0; i < arr.size(); i++) {def elt = arr[i]; echo \"running C-style loop on ${elt}\"; semaphore \"C-${elt}\"}\n" +
                    "for (def elt : arr) {echo \"running new-style loop on ${elt}\"; semaphore \"new-${elt}\"}"
                    , true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("C-one/1", b);
                rr.j.waitForMessage("running C-style loop on one", b);
            }
        });
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowRun b = rr.j.jenkins.getItemByFullName("p", WorkflowJob.class).getLastBuild();
                SemaphoreStep.success("C-one/1", null);
                SemaphoreStep.success("C-two/1", null);
                rr.j.waitForMessage("running C-style loop on two", b);
                SemaphoreStep.waitForStart("new-one/1", b);
                rr.j.waitForMessage("running new-style loop on one", b);
            }
        });
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowRun b = rr.j.jenkins.getItemByFullName("p", WorkflowJob.class).getLastBuild();
                SemaphoreStep.success("new-one/1", null);
                SemaphoreStep.success("new-two/1", null);
                rr.j.waitForCompletion(b);
                rr.j.assertBuildStatusSuccess(b);
                rr.j.assertLogContains("running new-style loop on two", b);
            }
        });
    }

    @Issue("JENKINS-34645")
    @Test public void stringSplit() {
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = rr.j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("for (x in 'a;b'.split(';')) {sleep 1; echo(/running in $x/)}", true));
                rr.j.assertLogContains("running in b", rr.j.buildAndAssertSuccess(p));
            }
        });
    }

    @Issue("JENKINS-27421")
    @Test public void mapIterator() {
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = rr.j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                    "def map = [one: 1, two: 2]\n" +
                    "@NonCPS def entrySet(m) {m.collect {k, v -> [key: k, value: v]}}; for (def e in entrySet(map)) {echo \"running flattened loop on ${e.key} -> ${e.value}\"; semaphore \"C-${e.key}\"}\n" +
                    "for (def e : map.entrySet()) {echo \"running new-style loop on ${e.key} -> ${e.value}\"; semaphore \"new-${e.key}\"}"
                    , true));
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("C-one/1", b);
                rr.j.waitForMessage("running flattened loop on one -> 1", b);
            }
        });
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowRun b = rr.j.jenkins.getItemByFullName("p", WorkflowJob.class).getLastBuild();
                SemaphoreStep.success("C-one/1", null);
                SemaphoreStep.success("C-two/1", null);
                rr.j.waitForMessage("running flattened loop on two -> 2", b);
                SemaphoreStep.waitForStart("new-one/1", b);
                rr.j.waitForMessage("running new-style loop on one -> 1", b);
            }
        });
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowRun b = rr.j.jenkins.getItemByFullName("p", WorkflowJob.class).getLastBuild();
                SemaphoreStep.success("new-one/1", null);
                SemaphoreStep.success("new-two/1", null);
                rr.j.waitForCompletion(b);
                rr.j.assertBuildStatusSuccess(b);
                rr.j.assertLogContains("running new-style loop on two -> 2", b);
            }
        });
    }

    @Issue("JENKINS-46597")
    @Test public void treeMapIterator() {
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = rr.j.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition(
                        "def map = new TreeMap<String,Integer>()\n" +
                                "map.put('one', 1)\n" +
                                "map.put('two', 2)\n" +
                                "@NonCPS def entrySet(m) {m.collect {k, v -> [key: k, value: v]}}; for (def e in entrySet(map)) {echo \"running flattened loop on ${e.key} -> ${e.value}\"; semaphore \"C-${e.key}\"}\n" +
                                "map.each { e -> echo \"running new-style loop on ${e.key} -> ${e.value}\"; semaphore \"new-${e.key}\"}"
                        , false)); // sandbox is false to allow new TreeMap
                WorkflowRun b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("C-one/1", b);
                rr.j.waitForMessage("running flattened loop on one -> 1", b);
            }
        });
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowRun b = rr.j.jenkins.getItemByFullName("p", WorkflowJob.class).getLastBuild();
                SemaphoreStep.success("C-one/1", null);
                SemaphoreStep.success("C-two/1", null);
                rr.j.waitForMessage("running flattened loop on two -> 2", b);
                SemaphoreStep.waitForStart("new-one/1", b);
                rr.j.waitForMessage("running new-style loop on one -> 1", b);
            }
        });
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowRun b = rr.j.jenkins.getItemByFullName("p", WorkflowJob.class).getLastBuild();
                SemaphoreStep.success("new-one/1", null);
                SemaphoreStep.success("new-two/1", null);
                rr.j.waitForCompletion(b);
                rr.j.assertBuildStatusSuccess(b);
                rr.j.assertLogContains("running new-style loop on two -> 2", b);
            }
        });
    }

    @Issue("JENKINS-27421")
    @Test public void otherIterators() {
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = rr.j.createProject(WorkflowJob.class, "p");
                // Map.keySet/values:
                p.setDefinition(new CpsFlowDefinition(
                    "def map = [one: 1, two: 2]\n" +
                    "def append(c) {def t = ''; sleep 1; for (def e : c) {t += e; sleep 1}; t}\n" +
                    "echo(/keys: ${append(map.keySet())} values: ${append(map.values())}/)"
                    , true));
                WorkflowRun b = rr.j.buildAndAssertSuccess(p);
                rr.j.assertLogContains("keys: onetwo values: 12", b);
                // List.listIterator:
                ScriptApproval.get().approveSignature("method java.util.List listIterator"); // TODO add to generic-whitelist
                ScriptApproval.get().approveSignature("method java.util.ListIterator set java.lang.Object"); // ditto
                p.setDefinition(new CpsFlowDefinition("def list = [1, 2, 3]; def itr = list.listIterator(); while (itr.hasNext()) {itr.set(itr.next() + 1); sleep 1}; echo(/new list: $list/)", true));
                rr.j.assertLogContains("new list: [2, 3, 4]", rr.j.buildAndAssertSuccess(p));
                // Set.iterator:
                p.setDefinition(new CpsFlowDefinition("def set = [1, 2, 3] as Set; def sum = 0; for (def e : set) {sum += e; sleep 1}; echo(/sum: $sum/)", true));
                rr.j.assertLogContains("sum: 6", rr.j.buildAndAssertSuccess(p));
            }
        });
    }

    @Issue("JENKINS-46597")
    @Test public void otherTreeMapIterators() {
        rr.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = rr.j.createProject(WorkflowJob.class, "p");
                // Map.keySet/values:
                p.setDefinition(new CpsFlowDefinition(
                        "def map = new TreeMap<String,Integer>()\n" +
                                "map.put('one', 1)\n" +
                                "map.put('two', 2)\n" +
                                "def append(c) {def t = ''; sleep 1; for (def e : c) {t += e; sleep 1}; t}\n" +
                                "echo(/keys: ${append(map.keySet())} values: ${append(map.values())}/)"
                        , false)); // Sandbox disabled so we can do new TreeMap
                WorkflowRun b = rr.j.buildAndAssertSuccess(p);
                rr.j.assertLogContains("keys: onetwo values: 12", b);
                // List.listIterator:
                ScriptApproval.get().approveSignature("method java.util.List listIterator"); // TODO add to generic-whitelist
                ScriptApproval.get().approveSignature("method java.util.ListIterator set java.lang.Object"); // ditto
                p.setDefinition(new CpsFlowDefinition("def list = [1, 2, 3]; def itr = list.listIterator(); while (itr.hasNext()) {itr.set(itr.next() + 1); sleep 1}; echo(/new list: $list/)", true));
                rr.j.assertLogContains("new list: [2, 3, 4]", rr.j.buildAndAssertSuccess(p));
                // Set.iterator:
                p.setDefinition(new CpsFlowDefinition("def set = [1, 2, 3] as Set; def sum = 0; for (def e : set) {sum += e; sleep 1}; echo(/sum: $sum/)", true));
                rr.j.assertLogContains("sum: 6", rr.j.buildAndAssertSuccess(p));
            }
        });
    }
}
