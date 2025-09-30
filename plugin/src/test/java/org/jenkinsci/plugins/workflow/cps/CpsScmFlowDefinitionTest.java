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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import hudson.Functions;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.scm.ChangeLogSet;
import hudson.triggers.SCMTrigger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import jenkins.model.Jenkins;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitStep;
import org.jenkinsci.plugins.workflow.TestDurabilityHintProvider;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;

public class CpsScmFlowDefinitionTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Rule
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Rule
    public GitSampleRepoRule invalidRepo = new GitSampleRepoRule();

    @Test
    public void configRoundtrip() throws Exception {
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        CpsScmFlowDefinition def =
                new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "Jenkinsfile");
        def.setLightweight(true);
        p.setDefinition(def);
        r.configRoundtrip(p);
        def = (CpsScmFlowDefinition) p.getDefinition();
        assertEquals("Jenkinsfile", def.getScriptPath());
        assertTrue(def.isLightweight());
        assertEquals(GitSCM.class, def.getScm().getClass());
    }

    @Test
    public void basics() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        CpsScmFlowDefinition def =
                new CpsScmFlowDefinition(new SingleFileSCM("flow.groovy", "echo 'hello from SCM'"), "flow.groovy");
        def.setLightweight(false); // currently the default, but just to be clear that we do rely on that in this test
        p.setDefinition(def);
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        // TODO currently the log text is in Run.log, but not on FlowStartNode/LogAction,
        // so not visible from Workflow Steps etc.
        r.assertLogContains("hello from SCM", b);
        r.assertLogContains("Staging flow.groovy", b);
        r.assertLogNotContains("Retrying after 10 seconds", b);
        FlowGraphWalker w = new FlowGraphWalker(b.getExecution());
        int workspaces = 0;
        for (FlowNode n : w) {
            if (n.getAction(WorkspaceAction.class) != null) {
                workspaces++;
            }
        }
        assertEquals(1, workspaces);
    }

    @Test
    public void changelogAndPolling() throws Exception {
        sampleRepo.init();
        sampleRepo.write("flow.groovy", "echo 'version one'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.addTrigger(new SCMTrigger("")); // no schedule, use notifyCommit only
        CpsScmFlowDefinition def =
                new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "flow.groovy");
        def.setLightweight(false);
        p.setDefinition(def);
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Cloning the remote Git repository", b);
        r.assertLogContains("version one", b);
        r.assertLogNotContains("Retrying after 10 seconds", b);
        sampleRepo.write("flow.groovy", "echo 'version two'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=next");
        sampleRepo.notifyCommit(r);
        b = p.getLastBuild();
        assertEquals(2, b.number);
        r.assertLogContains("Fetching changes from the remote Git repository", b);
        r.assertLogContains("version two", b);
        r.assertLogNotContains("Retrying after 10 seconds", b);
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
        assertEquals(1, changeSets.size());
        ChangeLogSet<? extends ChangeLogSet.Entry> changeSet = changeSets.get(0);
        assertEquals(b, changeSet.getRun());
        assertEquals("git", changeSet.getKind());
        Iterator<? extends ChangeLogSet.Entry> iterator = changeSet.iterator();
        assertTrue(iterator.hasNext());
        ChangeLogSet.Entry entry = iterator.next();
        assertEquals("[flow.groovy]", entry.getAffectedPaths().toString());
        assertFalse(iterator.hasNext());
    }

    @Issue("JENKINS-29881")
    @Test
    public void emptyChangeLogEmptyChangeSets() throws Exception {
        sampleRepo.init();
        sampleRepo.write("flow.groovy", "echo 'version one'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        CpsScmFlowDefinition def =
                new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "flow.groovy");
        def.setLightweight(false);
        p.setDefinition(def);
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Cloning the remote Git repository", b);
        r.assertLogContains("version one", b);
        r.assertLogNotContains("Retrying after 10 seconds", b);
        b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(2, b.number);
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
        assertEquals(Collections.emptyList(), changeSets);
    }

    @Issue({"JENKINS-33273", "JENKINS-63305"})
    @Test
    public void lightweight() throws Exception {
        sampleRepo.init();
        sampleRepo.write("flow.groovy", "echo 'version one'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        GitStep step = new GitStep(sampleRepo.toString());
        CpsScmFlowDefinition def = new CpsScmFlowDefinition(step.createSCM(), "flow.groovy");
        def.setLightweight(true);
        TestDurabilityHintProvider provider =
                Jenkins.get().getExtensionList(TestDurabilityHintProvider.class).get(0);
        provider.registerHint("p", FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
        p.setDefinition(def);
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        Assert.assertEquals(
                FlowDurabilityHint.PERFORMANCE_OPTIMIZED, b.getExecution().getDurabilityHint());
        r.assertLogNotContains("Cloning the remote Git repository", b);
        r.assertLogNotContains("Retrying after 10 seconds", b);
        r.assertLogContains("Obtained flow.groovy from git " + sampleRepo, b);
        r.assertLogContains("version one", b);
    }

    @Issue("JENKINS-42971")
    @Test
    public void lightweight_branch_parametrised() throws Exception {
        sampleRepo.init();
        sampleRepo.git("checkout", "-b", "master2");
        sampleRepo.write("flow.groovy", "echo 'version one'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("BRANCH", "")));
        GitSCM scm = new GitSCM(
                GitSCM.createRepoList(sampleRepo.toString(), null),
                Collections.singletonList(new BranchSpec("${BRANCH}")),
                null,
                null,
                Collections.emptyList());

        CpsScmFlowDefinition def = new CpsScmFlowDefinition(scm, "flow.groovy");
        def.setLightweight(true);
        p.setDefinition(def);

        r.assertBuildStatusSuccess(
                p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("BRANCH", "master2"))));
    }

    @Issue("JENKINS-59425")
    @Test
    public void missingFile() throws Exception {
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        GitStep step = new GitStep(sampleRepo.toString());
        CpsScmFlowDefinition def = new CpsScmFlowDefinition(step.createSCM(), "flow.groovy");
        def.setLightweight(true);
        p.setDefinition(def);
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogNotContains("Cloning the remote Git repository", b);
        r.assertLogNotContains("Retrying after 10 seconds", b);
        r.assertLogContains("Unable to find flow.groovy from git " + sampleRepo, b);
    }

    @Issue("JENKINS-39194")
    @Test
    public void retry() throws Exception {
        // We use an un-initialized repo here to test retry
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        GitStep step = new GitStep(invalidRepo.toString());
        CpsScmFlowDefinition def = new CpsScmFlowDefinition(step.createSCM(), "flow.groovy");
        def.setLightweight(false);
        p.setDefinition(def);
        r.jenkins.setScmCheckoutRetryCount(1);
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("Could not read from remote repository", b);
        r.assertLogContains("Retrying after 10 seconds", b);
    }

    @Issue("JENKINS-28447")
    @Test
    public void usingParameter() throws Exception {
        sampleRepo.init();
        sampleRepo.write("flow.groovy", "echo 'version one'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=one");
        sampleRepo.git("tag", "one");
        sampleRepo.write("flow.groovy", "echo 'version two'");
        sampleRepo.git("commit", "--all", "--message=two");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        CpsScmFlowDefinition def = new CpsScmFlowDefinition(
                new GitSCM(
                        List.of(new UserRemoteConfig(sampleRepo.fileUrl(), null, null, null)),
                        List.of(new BranchSpec("${VERSION}")),
                        false,
                        Collections.<SubmoduleConfig>emptyList(),
                        null,
                        null,
                        Collections.<GitSCMExtension>emptyList()),
                "flow.groovy");
        def.setLightweight(false); // TODO SCMFileSystem.of cannot pick up build parameters
        p.setDefinition(def);
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("VERSION", "master")));
        r.assertLogContains("version two", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
        r.assertLogContains(
                "version one",
                r.assertBuildStatusSuccess(
                        p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("VERSION", "one")))));
    }

    @Issue("JENKINS-42836")
    @Test
    public void usingParameterInScriptPath() throws Exception {
        sampleRepo.init();
        sampleRepo.write("flow.groovy", "echo 'version one'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.write("otherFlow.groovy", "echo 'version two'");
        sampleRepo.git("add", "otherFlow.groovy");
        sampleRepo.git("commit", "--all", "--message=commits");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        CpsScmFlowDefinition def = new CpsScmFlowDefinition(
                new GitSCM(
                        List.of(new UserRemoteConfig(sampleRepo.fileUrl(), null, null, null)),
                        List.of(new BranchSpec("master")),
                        false,
                        Collections.<SubmoduleConfig>emptyList(),
                        null,
                        null,
                        Collections.<GitSCMExtension>emptyList()),
                "${SCRIPT_PATH}");

        p.setDefinition(def);
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("SCRIPT_PATH", "flow.groovy")));
        r.assertLogContains("version one", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
        r.assertLogContains(
                "version two",
                r.assertBuildStatusSuccess(p.scheduleBuild2(
                        0, new ParametersAction(new StringParameterValue("SCRIPT_PATH", "otherFlow.groovy")))));
    }

    @Issue("SECURITY-2595")
    @Test
    public void scriptPathSymlinksCannotEscapeCheckoutDirectory() throws Exception {
        // On Windows, the symlink is treated as a regular file,
        // so there is no vulnerability, but the error message is different.
        assumeFalse(Functions.isWindows());
        sampleRepo.init();
        Path secrets = Paths.get(sampleRepo.getRoot().getPath(), "Jenkinsfile");
        Files.createSymbolicLink(secrets, Paths.get(r.jenkins.getRootDir() + "/secrets/master.key"));
        sampleRepo.git("add", ".");
        sampleRepo.git("commit", "-m", "init");

        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        GitStep step = new GitStep(sampleRepo.toString());
        p.setDefinition(new CpsScmFlowDefinition(step.createSCM(), "Jenkinsfile"));
        WorkflowRun b = r.buildAndAssertStatus(Result.FAILURE, p);
        assertThat(b.getExecution(), nullValue());
        r.assertLogContains("Jenkinsfile references a file that is not inside " + r.jenkins.getWorkspaceFor(p), b);
    }
}
