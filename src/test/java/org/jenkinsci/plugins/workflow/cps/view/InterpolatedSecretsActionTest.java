/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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
package org.jenkinsci.plugins.workflow.cps.view;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import java.util.Collections;

public class InterpolatedSecretsActionTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule();

    private WorkflowJob p;
    @Before
    public void newProject() throws Exception {
        p = r.createProject(WorkflowJob.class);
    }

    @Test
    public void testStepSignature() throws Exception {
        p.setDefinition(new CpsFlowDefinition("monomorphListStep([[firstArg:'one', secondArg:'two'], [firstArg:'three', secondArg:'four']])", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        LinearScanner scan = new LinearScanner();
        FlowNode node = scan.findFirstMatch(b.getExecution().getCurrentHeads().get(0), new NodeStepTypePredicate("monomorphListStep"));
        InterpolatedSecretsAction.InterpolatedWarnings warning =
                new InterpolatedSecretsAction.InterpolatedWarnings("monomorphListStep", Collections.emptyList(), b, node.getId());
        MatcherAssert.assertThat(warning.getStepSignature(), Matchers.is("monomorphListStep(data: [[firstArg: one, secondArg: two], [firstArg: three, secondArg: four]])"));
    }
}
