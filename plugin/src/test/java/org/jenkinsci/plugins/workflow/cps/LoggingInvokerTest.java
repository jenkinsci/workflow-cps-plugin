/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;

public class LoggingInvokerTest {

    @ClassRule public static JenkinsRule r = new JenkinsRule();
    @ClassRule public static final BuildWatcher bw = new BuildWatcher();

    @Test public void smokes() throws Exception {
        assertInternalCalls("currentBuild.rawBuild.description = 'XXX'; Jenkins.instance.systemMessage = 'XXX'", false,
            "hudson.model.Hudson.systemMessage",
            "jenkins.model.Jenkins.instance",
            "org.jenkinsci.plugins.workflow.job.WorkflowRun.description");
    }

    @Test public void groovyCalls() throws Exception {
        assertInternalCalls("class jenkinsHacks {}; echo(/created ${new jenkinsHacks()}/)", true);
    }

    @Test public void closures() throws Exception {
        assertInternalCalls("node {echo 'hello'}; [1, 2].collect {it + 1}", true);
    }

    @Test public void specialCalls() throws Exception {
        assertInternalCalls("new InterruptedBuildAction([])", false,
            "jenkins.model.InterruptedBuildAction.<init>");
        // TODO all of the receivers are of X from GroovyClassLoader so not recorded here:
        assertInternalCalls("class X extends hudson.lifecycle.Lifecycle {void onReady() {super.onReady()}}; new X().getHudsonWar(); new X().onReady()", false);
    }

    @Test public void envAction() throws Exception {
        assertInternalCalls("env.XXX = 'yyy'; echo XXX", true);
    }

    @Test public void runWrapper() throws Exception {
        assertInternalCalls("echo currentBuild.displayName", true);
    }

    private void assertInternalCalls(String script, boolean sandbox, String... calls) throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(script, sandbox));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        CpsFlowExecution exec = (CpsFlowExecution) b.getExecution();
        assertThat(exec.getInternalCalls(), containsInAnyOrder(calls));
    }

    @Test public void settersOnClosures() throws Exception {
        var p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("def c1 = {}; def c2 = {echo 'OK ran'}; c1.delegate = c2; c1.resolveStrategy = Closure.DELEGATE_FIRST; c1()", true));
        r.assertLogNotContains("`def`", r.buildAndAssertSuccess(p));
    }

    @Test public void closureToMapDslPattern() throws Exception {
        var p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                """
                def closureToMap(body) {
                  def map = [:]
                  body.resolveStrategy = Closure.DELEGATE_FIRST
                  body.delegate = map
                  body()
                  map
                }
                closureToMap {
                  key1 = 'value1'
                  key2 = 'value2'
                }
                """, true));
        r.assertLogNotContains("`def`", r.buildAndAssertSuccess(p));
    }

}
