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

import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.plugins.git.BranchSpec;
import hudson.plugins.git.GitChangeSet;
import hudson.plugins.git.GitChangeSetList;
import hudson.plugins.git.GitSCM;
import hudson.plugins.git.SubmoduleConfig;
import hudson.plugins.git.UserRemoteConfig;
import hudson.plugins.git.extensions.GitSCMExtension;
import hudson.scm.ChangeLogSet;
import hudson.triggers.SCMTrigger;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import jenkins.plugins.git.AbstractGitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import jenkins.plugins.git.GitStep;
import jenkins.scm.api.SCMHead;
import jenkins.scm.api.SCMRevisionAction;
import org.jenkinsci.plugins.workflow.actions.WorkspaceAction;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.SingleFileSCM;

public class CpsScmFlowDefinitionTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Test public void configRoundtrip() throws Exception {
        sampleRepo.init();
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        CpsScmFlowDefinition def = new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "Jenkinsfile");
        def.setLightweight(true);
        p.setDefinition(def);
        r.configRoundtrip(p);
        def = (CpsScmFlowDefinition) p.getDefinition();
        assertEquals("Jenkinsfile", def.getScriptPath());
        assertTrue(def.isLightweight());
        assertEquals(GitSCM.class, def.getScm().getClass());
    }

    @Test public void basics() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        CpsScmFlowDefinition def = new CpsScmFlowDefinition(new SingleFileSCM("flow.groovy", "echo 'hello from SCM'"), "flow.groovy");
        def.setLightweight(false); // currently the default, but just to be clear that we do rely on that in this test
        p.setDefinition(def);
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        // TODO currently the log text is in Run.log, but not on FlowStartNode/LogAction, so not visible from Workflow Steps etc.
        r.assertLogContains("hello from SCM", b);
        r.assertLogContains("Staging flow.groovy", b);
        FlowGraphWalker w = new FlowGraphWalker(b.getExecution());
        int workspaces = 0;
        for (FlowNode n : w) {
            if (n.getAction(WorkspaceAction.class) != null) {
                workspaces++;
            }
        }
        assertEquals(1, workspaces);
    }

    @Test public void changelogAndPolling() throws Exception {
        sampleRepo.init();
        sampleRepo.write("flow.groovy", "echo 'version one'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.addTrigger(new SCMTrigger("")); // no schedule, use notifyCommit only
        CpsScmFlowDefinition def = new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "flow.groovy");
        def.setLightweight(false);
        p.setDefinition(def);
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Cloning the remote Git repository", b);
        r.assertLogContains("version one", b);
        sampleRepo.write("flow.groovy", "echo 'version two'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=next");
        sampleRepo.notifyCommit(r);
        b = p.getLastBuild();
        assertEquals(2, b.number);
        r.assertLogContains("Fetching changes from the remote Git repository", b);
        r.assertLogContains("version two", b);
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
    @Test public void emptyChangeLogEmptyChangeSets() throws Exception {
        sampleRepo.init();
        sampleRepo.write("flow.groovy", "echo 'version one'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        CpsScmFlowDefinition def = new CpsScmFlowDefinition(new GitStep(sampleRepo.toString()).createSCM(), "flow.groovy");
        def.setLightweight(false);
        p.setDefinition(def);
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Cloning the remote Git repository", b);
        r.assertLogContains("version one", b);
        b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertEquals(2, b.number);
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
        assertEquals(Collections.emptyList(), changeSets);
    }

    @Issue("JENKINS-33273")
    @Test public void lightweight() throws Exception {
        sampleRepo.init();
        sampleRepo.write("flow.groovy", "echo 'version one'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        GitStep step = new GitStep(sampleRepo.toString());
        CpsScmFlowDefinition def = new CpsScmFlowDefinition(step.createSCM(), "flow.groovy");
        def.setLightweight(true);
        p.setDefinition(def);
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogNotContains("Cloning the remote Git repository", b);
        r.assertLogContains("Obtained flow.groovy from git " + sampleRepo, b);
        r.assertLogContains("version one", b);
    }

    @Ignore("Requires way to signal changeset to WorkflowRun")
    @Test public void lightweightChangelog() throws Exception {
        sampleRepo.init();
        sampleRepo.write("flow.groovy", "echo 'version one'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=init");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        GitStep step = new GitStep(sampleRepo.toString());
        CpsScmFlowDefinition def = new CpsScmFlowDefinition(step.createSCM(), "flow.groovy");
        def.setLightweight(true);
        p.setDefinition(def);
        // we add the SCMRevisionAction because we are hijacking a plain SCM as a proxy for a SCMSource backed SCM
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0,
                new SCMRevisionAction(new AbstractGitSCMSource.SCMRevisionImpl(
                        new SCMHead("master"), sampleRepo.head()
                ))
        ));
        r.assertLogNotContains("Cloning the remote Git repository", b);
        r.assertLogContains("Obtained flow.groovy from git " + sampleRepo, b);
        r.assertLogContains("version one", b);
        List<ChangeLogSet<? extends ChangeLogSet.Entry>> changeSets = b.getChangeSets();
        assertEquals("First build traditionally has empty changes", Collections.emptyList(), changeSets);
        sampleRepo.write("flow.groovy", "echo 'version two'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=update");
        // we add the SCMRevisionAction because we are hijacking a plain SCM as a proxy for a SCMSource backed SCM
        b = r.assertBuildStatusSuccess(p.scheduleBuild2(0,
                new SCMRevisionAction(new AbstractGitSCMSource
                .SCMRevisionImpl(new SCMHead("master"), sampleRepo.head()
                ))
        ));
        changeSets = b.getChangeSets();
        assertEquals(1, changeSets.size());
        ChangeLogSet<? extends ChangeLogSet.Entry> entry = changeSets.get(0);
        assertEquals(GitChangeSet.class, entry.getItems()[0].getClass());
        assertEquals("update", ((GitChangeSet)entry.getItems()[0]).getMsg());
    }

    @Issue("JENKINS-28447")
    @Test public void usingParameter() throws Exception {
        sampleRepo.init();
        sampleRepo.write("flow.groovy", "echo 'version one'");
        sampleRepo.git("add", "flow.groovy");
        sampleRepo.git("commit", "--message=one");
        sampleRepo.git("tag", "one");
        sampleRepo.write("flow.groovy", "echo 'version two'");
        sampleRepo.git("commit", "--all", "--message=two");
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        CpsScmFlowDefinition def = new CpsScmFlowDefinition(new GitSCM(Collections.singletonList(new UserRemoteConfig(sampleRepo.fileUrl(), null, null, null)),
            Collections.singletonList(new BranchSpec("${VERSION}")),
            false, Collections.<SubmoduleConfig>emptyList(), null, null, Collections.<GitSCMExtension>emptyList()), "flow.groovy");
        def.setLightweight(false); // TODO SCMFileSystem.of cannot pick up build parameters
        p.setDefinition(def);
        p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("VERSION", "master")));
        r.assertLogContains("version two", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
        r.assertLogContains("version one", r.assertBuildStatusSuccess(p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("VERSION", "one")))));
    }

}
