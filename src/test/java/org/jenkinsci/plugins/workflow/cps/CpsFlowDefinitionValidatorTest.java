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

package org.jenkinsci.plugins.workflow.cps;

import static org.hamcrest.Matchers.containsString;

import hudson.model.Item;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Rule;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class CpsFlowDefinitionValidatorTest {

    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void doCheckScriptCompile() throws Exception {
        CpsFlowDefinition.DescriptorImpl d = r.jenkins.getDescriptorByType(CpsFlowDefinition.DescriptorImpl.class);
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "w");
        assertThat(d.doCheckScriptCompile(job, "echo 'hello'").toString(), containsString("\"success\""));
        assertThat(d.doCheckScriptCompile(job, "echo 'hello").toString(), containsString("\"fail\""));
    }

    @Issue("SECURITY-1266")
    @Test
    public void blockASTTest() throws Exception {
        CpsFlowDefinition.DescriptorImpl d = r.jenkins.getDescriptorByType(CpsFlowDefinition.DescriptorImpl.class);
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "w");
        assertThat(d.doCheckScriptCompile(job, "import groovy.transform.*\n" +
                "import jenkins.model.Jenkins\n" +
                "import org.jenkinsci.plugins.workflow.job.WorkflowJob\n" +
                "@ASTTest(value={ assert Jenkins.get().createProject(WorkflowJob.class, \"should-not-exist\") })\n" +
                "@Field int x\n" +
                "echo 'hello'\n").toString(), containsString("Annotation ASTTest cannot be used in the sandbox"));

        assertNull(r.jenkins.getItem("should-not-exist"));
    }

    @Issue("SECURITY-1266")
    @Test
    public void blockGrab() throws Exception {
        CpsFlowDefinition.DescriptorImpl d = r.jenkins.getDescriptorByType(CpsFlowDefinition.DescriptorImpl.class);
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "w");
        assertThat(d.doCheckScriptCompile(job, "@Grab(group='foo', module='bar', version='1.0')\n" +
                "def foo\n").toString(), containsString("Annotation Grab cannot be used in the sandbox"));
    }

    @Issue("SECURITY-1266")
    @Test
    public void configureRequired() throws Exception {
        CpsFlowDefinition.DescriptorImpl d = r.jenkins.getDescriptorByType(CpsFlowDefinition.DescriptorImpl.class);

        r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
        // Set up an administrator, and three developer users with varying levels of access.
        r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy().
                grant(Jenkins.ADMINISTER).everywhere().to("admin").
                grant(Jenkins.READ, Item.CONFIGURE).everywhere().to("dev1").
                grant(Jenkins.READ).everywhere().to("dev2"));
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "w");

        try (ACLContext context = ACL.as(User.getById("admin", true))) {
            assertThat(d.doCheckScriptCompile(job, "echo 'hello").toString(), containsString("fail"));
        }
        try (ACLContext context = ACL.as(User.getById("dev1", true))) {
            assertThat(d.doCheckScriptCompile(job, "echo 'hello").toString(), containsString("fail"));
        }
        try (ACLContext context = ACL.as(User.getById("dev2", true))) {
            assertThat(d.doCheckScriptCompile(job, "echo 'hello").toString(), containsString("success"));
        }
    }

    @Issue("SECURITY-1336")
    @Test
    public void blockConstructorInvocationInCheck() throws Exception {
        CpsFlowDefinition.DescriptorImpl d = r.jenkins.getDescriptorByType(CpsFlowDefinition.DescriptorImpl.class);
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "w");
        assertThat(d.doCheckScriptCompile(job, "import jenkins.model.Jenkins\n" +
                "import hudson.model.FreeStyleProject\n" +
                "public class DoNotRunConstructor {\n" +
                "  public DoNotRunConstructor() {\n" +
                "    assert Jenkins.getInstance().createProject(FreeStyleProject.class, \"should-not-exist\")\n" +
                "  }\n" +
                "}\n").toString(), containsString("success"));

        assertNull(r.jenkins.getItem("should-not-exist"));
    }

}
