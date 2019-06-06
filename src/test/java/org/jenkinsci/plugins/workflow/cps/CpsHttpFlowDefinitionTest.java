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

import hudson.model.Result;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import static org.junit.Assert.assertEquals;

public class CpsHttpFlowDefinitionTest {
    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void testRunJenkinsHomePageAsPipeline() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        CpsHttpFlowDefinition def = new CpsHttpFlowDefinition(r.jenkins.getRootUrl(), "", 3);
        p.setDefinition(def);
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("unexpected token", b);
    }

    @Test public void testRetryCount() throws Exception {
        String scriptUrl = "https://bad-website-jenkins-test.com/Jenkinsfile";
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        CpsHttpFlowDefinition def = new CpsHttpFlowDefinition(scriptUrl, "", 3);
        p.setDefinition(def);
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("Caught exception while fetching " + scriptUrl, b);
        assertEquals(3, StringUtils.countMatches(r.getLog(b), "Retrying"));
    }

}
