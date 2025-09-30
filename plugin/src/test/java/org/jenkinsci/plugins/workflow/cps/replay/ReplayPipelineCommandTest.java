/*
 * The MIT License
 *
 * Copyright 2024 CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps.replay;

import static org.hamcrest.MatcherAssert.assertThat;

import hudson.cli.CLICommandInvoker;
import jenkins.model.Jenkins;
import org.apache.tools.ant.filters.StringInputStream;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

public class ReplayPipelineCommandTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Issue("SECURITY-3362")
    @Test
    public void rebuildNeedScriptApprovalCLIEdition() throws Exception {
        j.jenkins.setSecurityRealm(j.createDummySecurityRealm());
        j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                .grant(Jenkins.ADMINISTER)
                .everywhere()
                .toEveryone());

        WorkflowJob p = j.jenkins.createProject(WorkflowJob.class, "SECURITY-3362");
        j.jenkins.getWorkspaceFor(p).child("a.groovy").write("echo 'Hello LoadedWorld'", null);
        String script = "def a\n" + "node() {\n" + "    a = load('a.groovy')\n" + "}\n";
        p.setDefinition(new CpsFlowDefinition(script, false));

        ScriptApproval.get().preapprove(script, GroovyLanguage.get());

        WorkflowRun b1 = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        j.assertBuildStatusSuccess(j.waitForCompletion(b1));
        String viaCliScript = "echo 'HelloWorld'";

        assertThat(
                new CLICommandInvoker(j, new ReplayPipelineCommand())
                        .withStdin(new StringInputStream(viaCliScript))
                        .invokeWithArgs(p.getName(), "-n", "1"),
                CLICommandInvoker.Matcher.succeededSilently());
        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(p.getBuildByNumber(2));

        assertThat(
                new CLICommandInvoker(j, new ReplayPipelineCommand())
                        .withStdin(new StringInputStream(viaCliScript))
                        .invokeWithArgs(p.getName(), "-n", "1", "-s", "Script1"),
                CLICommandInvoker.Matcher.succeededSilently());
        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(p.getBuildByNumber(3));

        ScriptApproval.get().clearApprovedScripts();

        assertThat(
                new CLICommandInvoker(j, new ReplayPipelineCommand())
                        .withStdin(new StringInputStream(viaCliScript))
                        .invokeWithArgs(p.getName(), "-n", "1"),
                CLICommandInvoker.Matcher.succeededSilently());
        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(p.getBuildByNumber(4));

        ScriptApproval.get().clearApprovedScripts();

        assertThat(
                new CLICommandInvoker(j, new ReplayPipelineCommand())
                        .withStdin(new StringInputStream(viaCliScript))
                        .invokeWithArgs(p.getName(), "-n", "1", "-s", "Script1"),
                CLICommandInvoker.Matcher.failedWith(1));

        assertThat(
                new CLICommandInvoker(j, new ReplayPipelineCommand())
                        .withStdin(new StringInputStream(viaCliScript))
                        .invokeWithArgs(p.getName(), "-n", "1", "-s", "Script1", "-a"),
                CLICommandInvoker.Matcher.succeededSilently());
        j.waitUntilNoActivity();
        j.assertBuildStatusSuccess(p.getBuildByNumber(5));
    }
}
