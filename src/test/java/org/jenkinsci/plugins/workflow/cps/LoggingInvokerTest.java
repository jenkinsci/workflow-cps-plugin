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

import java.util.Arrays;
import java.util.TreeSet;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.jvnet.hudson.test.JenkinsRule;

public class LoggingInvokerTest {

    @ClassRule public static JenkinsRule r = new JenkinsRule();

    @Test public void smokes() throws Exception {
        assertInternalCalls("currentBuild.rawBuild.description = 'XXX'; Jenkins.instance.systemMessage = 'XXX'", false,
            "hudson.model.Hudson.systemMessage",
            "jenkins.model.Jenkins.instance",
            "org.jenkinsci.plugins.workflow.job.WorkflowRun.description",
            "org.jenkinsci.plugins.workflow.support.steps.build.RunWrapper.rawBuild");
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

    private void assertInternalCalls(String script, boolean sandbox, String... calls) throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(script, sandbox));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        CpsFlowExecution exec = (CpsFlowExecution) b.getExecution();
        assertEquals(new TreeSet<>(Arrays.asList(calls)).toString(), exec.getInternalCalls().toString());
    }

}
