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

import hudson.model.BooleanParameterDefinition;
import hudson.model.BooleanParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterDefinition;
import hudson.model.PasswordParameterValue;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class ParamsVariableTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Issue("JENKINS-27295")
    @Test
    public void smokes() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                "echo(/TEXT=${params.TEXT} FLAG=${params.FLAG ? 'yes' : 'no'} PASS=${params.PASS}/)", true));
        p.addProperty(new ParametersDefinitionProperty(
                new StringParameterDefinition("TEXT", ""),
                new BooleanParameterDefinition("FLAG", false, null),
                new PasswordParameterDefinition("PASS", "", null)));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(
                0,
                new ParametersAction(
                        new StringParameterValue("TEXT", "hello"),
                        new BooleanParameterValue("FLAG", true),
                        new PasswordParameterValue("PASS", "s3cr3t"))));
        r.assertLogContains("TEXT=hello", b);
        r.assertLogContains("FLAG=yes", b);
        r.assertLogContains("PASS=s3cr3t", b);
    }
}
