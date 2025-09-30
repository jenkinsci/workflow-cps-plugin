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

package org.jenkinsci.plugins.workflow.cps.replay;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import hudson.FilePath;
import hudson.XmlFile;
import hudson.cli.CLICommandInvoker;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Failure;
import hudson.model.Item;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.PasswordParameterDefinition;
import hudson.model.PasswordParameterValue;
import hudson.model.Run;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.ACLContext;
import hudson.security.Permission;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.htmlunit.FailingHttpStatusCodeException;
import org.htmlunit.WebAssert;
import org.htmlunit.html.HtmlForm;
import org.htmlunit.html.HtmlPage;
import org.htmlunit.html.HtmlTextArea;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;
import org.jvnet.hudson.test.RestartableJenkinsRule;

public class ReplayActionTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Test
    public void editSimpleDefinition() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("echo 'first script'", false));
                // Start off with a simple run of the first script.
                WorkflowRun b1 = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("first script", b1);
                // Now will replay with a second script.
                WorkflowRun b2;
                { // First time around, verify that UI elements are present and functional.
                    ReplayAction a = b1.getAction(ReplayAction.class);
                    assertNotNull(a);
                    assertTrue(a.isEnabled());
                    HtmlPage page = story.j.createWebClient().getPage(b1, a.getUrlName());
                    HtmlForm form = page.getFormByName("config");
                    HtmlTextArea text = form.getTextAreaByName("_.mainScript");
                    assertEquals("echo 'first script'", text.getText());
                    text.setText("echo 'second script'");
                    // TODO loaded scripts
                    HtmlPage redirect = story.j.submit(form);
                    assertEquals(p.getAbsoluteUrl(), redirect.getUrl().toString());
                    story.j.waitUntilNoActivity();
                    b2 = p.getBuildByNumber(2);
                    assertNotNull(b2);
                } // Subsequently can use the faster whitebox method.
                story.j.assertLogContains("second script", story.j.assertBuildStatusSuccess(b2));
                ReplayCause cause = b2.getCause(ReplayCause.class);
                assertNotNull(cause);
                assertEquals(1, cause.getOriginalNumber());
                assertEquals(b1, cause.getOriginal());
                assertEquals(b2, cause.getRun());
                // Replay #2 as #3. Note that the diff is going to be from #1 → #3, not #2 → #3.
                WorkflowRun b3 = (WorkflowRun) b2.getAction(ReplayAction.class)
                        .run("echo 'third script'", Collections.<String, String>emptyMap())
                        .get();
                story.j.assertLogContains("third script", story.j.assertBuildStatusSuccess(b3));
                String diff = b3.getAction(ReplayAction.class).getDiff();
                assertThat(diff, containsString("-echo 'first script'"));
                assertThat(diff, containsString("+echo 'third script'"));
                System.out.println(diff);
                // Make a persistent edit to the script and run, just to make sure there is no lingering effect.
                p.setDefinition(new CpsFlowDefinition("echo 'fourth script'", false));
                story.j.assertLogContains("fourth script", story.j.assertBuildStatusSuccess(p.scheduleBuild2(0)));
            }
        });
    }

    @Test
    public void parameterized() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("param", "")));
                p.setDefinition(new CpsFlowDefinition("echo \"run with ${param}\"", true));
                WorkflowRun b1 = story.j.assertBuildStatusSuccess(
                        p.scheduleBuild2(0, new ParametersAction(new StringParameterValue("param", "some value"))));
                story.j.assertLogContains("run with some value", b1);
                // When we replay a parameterized build, we expect the original parameter values to be set.
                WorkflowRun b2 = (WorkflowRun) b1.getAction(ReplayAction.class)
                        .run("echo \"run again with ${param}\"", Collections.<String, String>emptyMap())
                        .get();
                story.j.assertLogContains("run again with some value", story.j.assertBuildStatusSuccess(b2));
            }
        });
    }

    @Issue("SECURITY-2443")
    @Test
    public void withPasswordParameter() {
        story.then(r -> {
            WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
            p.addProperty(new ParametersDefinitionProperty(
                    new PasswordParameterDefinition("passwordParam", "top secret", "")));
            p.setDefinition(new CpsFlowDefinition("echo(/passwordParam: ${passwordParam}/)", true));
            WorkflowRun run1 = story.j.assertBuildStatusSuccess(p.scheduleBuild2(
                    0, new ParametersAction(new PasswordParameterValue("passwordParam", "confidential"))));

            // When we replay a build with password parameter it should fail with access denied exception.
            assertThrows(Failure.class, () -> run1.getAction(ReplayAction.class)
                    .run("echo(/Replaying passwordParam: ${passwordParam}/)", Collections.emptyMap())
                    .get());
        });
    }

    @Issue("JENKINS-50784")
    @Test
    public void lazyLoadExecutionStillReplayable() throws Exception {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            WorkflowJob p2 = r.jenkins.createProject(WorkflowJob.class, "p2");
            p.setDefinition(new CpsFlowDefinition("echo 'I did a thing'", false));
            p2.setDefinition(new CpsFlowDefinition("echo 'I did a thing'", true));
            // Start off with a simple run of the first script.
            r.buildAndAssertSuccess(p);
            r.buildAndAssertSuccess(p2);

            r.jenkins.setSecurityRealm(r.createDummySecurityRealm());
            r.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                    .grant(Jenkins.ADMINISTER)
                    .everywhere()
                    .to("admin")
                    .grant(ReplayAction.REPLAY)
                    .everywhere()
                    .to("normal"));
        });
        story.then(r -> {
            WorkflowJob job = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowJob job2 = r.jenkins.getItemByFullName("p2", WorkflowJob.class);
            WorkflowRun run = job.getLastBuild();
            WorkflowRun run2 = job2.getLastBuild();

            JenkinsRule.WebClient wc = r.createWebClient();
            Assert.assertNull(run.asFlowExecutionOwner().getOrNull());
            assertTrue(canReplay(run, "admin"));
            assertTrue(canReplay(run, "normal"));
            assertTrue(canRebuild(run, "admin"));
            Assert.assertNull(run.asFlowExecutionOwner().getOrNull());

            // After lazy-load we can do deeper checks easily, and the deep test triggers a full load of the execution
            assertTrue(canReplayDeepTest(run, "admin"));
            assertTrue(canReplayDeepTest(run2, "normal"));

            assertNotNull(run.asFlowExecutionOwner().getOrNull());
            assertTrue(canReplay(run, "admin"));
            assertFalse(canReplay(
                    run, "normal")); // Now we know to check if the user can run outside sandbox, and they can't
            assertTrue(canReplay(run2, "normal")); // We can still run stuff inside sandbox
            assertTrue(canRebuild(run, "admin"));
        });
    }

    @Initializer(
            after = InitMilestone.EXTENSIONS_AUGMENTED,
            before =
                    InitMilestone
                            .JOB_LOADED) // same time as Jenkins global config is loaded (e.g., AuthorizationStrategy)
    public static void assertPermissionId() {
        String thePermissionId = "hudson.model.Run.Replay";
        // An AuthorizationStrategy may be loading a permission by name during Jenkins startup.
        Permission thePermission = Permission.fromId(thePermissionId);
        // Make sure it finds this addition, even though the PermissionGroup is in core.
        assertEquals(ReplayAction.REPLAY, thePermission);
        assertEquals(thePermissionId, thePermission.getId());
    }

    @Test
    public void permissions() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                // assertPermissionId should have been run before we get here
                story.j.jenkins.setSecurityRealm(story.j.createDummySecurityRealm());
                // Set up an administrator, and three developer users with varying levels of access.
                List<Permission> permissions = Run.PERMISSIONS.getPermissions();
                assertThat(permissions, Matchers.hasItem(ReplayAction.REPLAY));
                story.j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                        .grant(Jenkins.ADMINISTER)
                        .everywhere()
                        .to("admin")
                        .grant(Jenkins.READ, /* implies REPLAY */ Item.CONFIGURE)
                        .everywhere()
                        .to("dev1")
                        .grant(Jenkins.READ, ReplayAction.REPLAY)
                        .everywhere()
                        .to("dev2")
                        .grant(Jenkins.READ, /* does not imply REPLAY, does allow rebuilding */ Item.BUILD)
                        .everywhere()
                        .to("dev3"));
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("", /* whole-script approval */ false));
                WorkflowRun b1 = p.scheduleBuild2(0).get();
                // Jenkins admins can of course do as they please. But developers without ADMINISTER are out of luck.
                assertTrue(canReplay(b1, "admin"));
                assertFalse("not sandboxed, so only safe for admins", canReplay(b1, "dev1"));
                assertFalse(canReplay(b1, "dev2"));
                assertFalse(canReplay(b1, "dev3"));
                assertTrue(canRebuild(b1, "dev3"));
                p.setDefinition(new CpsFlowDefinition("", /* sandbox */ true));
                WorkflowRun b2 = p.scheduleBuild2(0).get();
                assertTrue(canReplay(b2, "admin"));
                // Developers with REPLAY (or CONFIGURE) can run it.
                assertTrue(canReplay(b2, "dev1"));
                assertTrue(canReplay(b2, "dev2"));
                assertFalse(canReplay(b2, "dev3"));
                assertTrue(canRebuild(b2, "dev3"));
                // Disable the job and verify that no one can replay it.
                p.makeDisabled(true);
                assertFalse(canReplay(b2, "admin"));
                assertFalse(canReplay(b2, "dev1"));
                assertFalse(canReplay(b2, "dev2"));
                assertFalse(canReplay(b2, "dev3"));
                assertFalse(canRebuild(b2, "dev3"));
            }
        });
    }

    private static boolean canReplay(WorkflowRun b, String user) {
        ReplayAction a = b.getAction(ReplayAction.class);
        try (ACLContext context = ACL.as(User.getById(user, true))) {
            return a.isEnabled();
        }
    }

    private static boolean canReplayDeepTest(WorkflowRun b, String user) {
        ReplayAction a = b.getAction(ReplayAction.class);
        try (ACLContext context = ACL.as(User.getById(user, true))) {
            return a.isReplayableSandboxTest();
        }
    }

    private static boolean canRebuild(WorkflowRun b, String user) {
        ReplayAction a = b.getAction(ReplayAction.class);
        try (ACLContext context = ACL.as(User.getById(user, true))) {
            return a.isRebuildEnabled();
        }
    }

    @Test
    public void loadStep() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                // Shortcut to simulate checking out an external repo with an auxiliary script.
                story.j.jenkins.getWorkspaceFor(p).child("f1.groovy").write("echo 'original first part'", null);
                story.j.jenkins.getWorkspaceFor(p).child("f2.groovy").write("echo 'original second part'", null);
                p.setDefinition(new CpsFlowDefinition(
                        "node {load 'f1.groovy'}; semaphore 'wait'; node {load 'f2.groovy'}", true));
                // Initial build loads external script and prints a message.
                SemaphoreStep.success("wait/1", null);
                WorkflowRun b1 = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("original first part", b1);
                story.j.assertLogContains("original second part", b1);
                // Editing main script to print an initial message, and editing one of the loaded scripts as well.
                SemaphoreStep.success("wait/2", null);
                WorkflowRun b2 = (WorkflowRun) b1.getAction(ReplayAction.class)
                        .run(
                                "echo 'trying edits'\nnode {load 'f1.groovy'}; semaphore 'wait'; node {load 'f2.groovy'}",
                                Map.of("Script2", "echo 'new second part'"))
                        .get();
                story.j.assertBuildStatusSuccess(b2);
                story.j.assertLogContains("trying edits", b2);
                story.j.assertLogContains("original first part", b2);
                story.j.assertLogContains("new second part", b2);
                // Can take a look at the build.xml and see that we are not duplicating script content once edits are
                // applied (not yet formally asserted).
                System.out.println(new XmlFile(new File(b2.getRootDir(), "build.xml")).asString());
                // Diff should reflect both sets of changes.
                String diff = b2.getAction(ReplayAction.class).getDiff();
                assertThat(diff, containsString("+echo 'trying edits'"));
                assertThat(diff, containsString("Script2"));
                assertThat(diff, containsString("-echo 'original second part'"));
                assertThat(diff, containsString("+echo 'new second part'"));
                assertThat(diff, not(containsString("Script1")));
                assertThat(diff, not(containsString("first part")));
                System.out.println(diff);
                // Now replay #2, editing all scripts, and restarting in the middle.
                Map<String, String> replayMap =
                        Map.of("Script1", "echo 'new first part'", "Script2", "echo 'newer second part'");
                WorkflowRun b3 = (WorkflowRun) b2.getAction(ReplayAction.class)
                        .run("node {load 'f1.groovy'}; semaphore 'wait'; node {load 'f2.groovy'}", replayMap)
                        .waitForStart();
                SemaphoreStep.waitForStart("wait/3", b3);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b3 = p.getLastBuild();
                assertEquals(3, b3.getNumber());
                // Resume #3, and verify that the build completes with the expected replacements.
                SemaphoreStep.success("wait/3", null);
                story.j.waitForCompletion(b3);
                story.j.assertLogNotContains("trying edits", b3);
                story.j.assertLogContains("new first part", b3);
                story.j.assertLogContains("newer second part", b3);
            }
        });
    }

    @Test
    public void cli() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                // As in loadStep, will set up a main and auxiliary script.
                FilePath f = story.j.jenkins.getWorkspaceFor(p).child("f.groovy");
                f.write("'original text'", null);
                p.setDefinition(new CpsFlowDefinition("node {def t = load 'f.groovy'; echo \"got ${t}\"}", true));
                WorkflowRun b1 = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("got original text", b1);
                // s/got/received/ on main script
                assertEquals(
                        0,
                        new CLICommandInvoker(story.j, "replay-pipeline")
                                .withStdin(IOUtils.toInputStream(
                                        "node {def t = load 'f.groovy'; echo \"received ${t}\"}",
                                        StandardCharsets.UTF_8))
                                .invokeWithArgs("p")
                                .returnCode());
                story.j.waitUntilNoActivity();
                WorkflowRun b2 = p.getLastBuild();
                assertEquals(2, b2.getNumber());
                story.j.assertLogContains("received original text", b2);
                // s/original/new/ on auxiliary script, and explicitly asking to replay #1 rather than the latest
                assertEquals(
                        0,
                        new CLICommandInvoker(story.j, "replay-pipeline")
                                .withStdin(IOUtils.toInputStream("'new text'", StandardCharsets.UTF_8))
                                .invokeWithArgs("p", "-n", "1", "-s", "Script1")
                                .returnCode());
                story.j.waitUntilNoActivity();
                WorkflowRun b3 = p.getLastBuild();
                assertEquals(3, b3.getNumber());
                // Main script picked up from #1, not #2.
                story.j.assertLogContains("got new text", b3);
            }
        });
    }

    @Issue("JENKINS-47339")
    @Test
    public void rebuild() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                story.j.jenkins.setSecurityRealm(story.j.createDummySecurityRealm());
                story.j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                        .grant(Jenkins.READ, Item.BUILD, Item.READ)
                        .everywhere()
                        .to("dev3"));

                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                p.setDefinition(new CpsFlowDefinition("echo 'script to rebuild'", true));
                WorkflowRun b1 = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("script to rebuild", b1);

                WorkflowRun b2;
                { // First time around, verify that UI elements are present and functional.
                    ReplayAction a = b1.getAction(ReplayAction.class);
                    assertNotNull(a);
                    assertFalse(canReplay(b1, "dev3"));
                    assertTrue(canRebuild(b1, "dev3"));
                    JenkinsRule.WebClient wc = story.j.createWebClient();
                    wc.login("dev3");
                    HtmlPage page = wc.getPage(b1, a.getUrlName());
                    WebAssert.assertFormNotPresent(page, "config");
                    HtmlForm form = page.getFormByName("rebuild");
                    HtmlPage redirect = story.j.submit(form);
                    assertEquals(p.getAbsoluteUrl(), redirect.getUrl().toString());
                    story.j.waitUntilNoActivity();
                    b2 = p.getBuildByNumber(2);
                    assertNotNull(b2);
                }
                story.j.assertLogContains("script to rebuild", story.j.assertBuildStatusSuccess(b2));
                ReplayCause cause = b2.getCause(ReplayCause.class);
                assertNotNull(cause);
                assertEquals(1, cause.getOriginalNumber());
                assertEquals(b1, cause.getOriginal());
                assertEquals(b2, cause.getRun());
            }
        });
    }

    @Issue("SECURITY-3362")
    @Test
    public void rebuildNeedScriptApproval() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                story.j.jenkins.setSecurityRealm(story.j.createDummySecurityRealm());
                story.j.jenkins.setAuthorizationStrategy(new MockAuthorizationStrategy()
                        .grant(Jenkins.READ, Item.BUILD, Item.READ)
                        .everywhere()
                        .to("dev1"));

                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "SECURITY-3362");
                String script = "pipeline {\n" + "  agent any\n"
                        + "  stages {\n"
                        + "    stage('List Jobs') {\n"
                        + "      steps {\n"
                        + "        script {\n"
                        + "           println \"Jobs: ${jenkins.model.Jenkins.instance.getItemByFullName(env.JOB_NAME)?.parent?.items*.fullName.join(', ')}!\""
                        + "        }\n"
                        + "      }\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n";
                p.setDefinition(new CpsFlowDefinition(script, false));

                ScriptApproval.get().preapprove(script, GroovyLanguage.get());

                WorkflowRun b1 = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b1));

                ScriptApproval.get().clearApprovedScripts();

                { // First time around, verify that UI elements are present and functional.
                    ReplayAction a = b1.getAction(ReplayAction.class);
                    assertNotNull(a);
                    assertFalse(canReplay(b1, "dev1"));
                    assertTrue(canRebuild(b1, "dev1"));
                    JenkinsRule.WebClient wc = story.j.createWebClient();
                    wc.login("dev1");

                    HtmlPage page = wc.getPage(b1, a.getUrlName());
                    WebAssert.assertFormNotPresent(page, "config");
                    HtmlForm form = page.getFormByName("rebuild");

                    try {
                        story.j.submit(form);
                    } catch (FailingHttpStatusCodeException e) {
                        String responseBody = e.getResponse().getContentAsString();
                        assertTrue(responseBody.contains("The script is not approved."));
                    }
                    story.j.waitUntilNoActivity();
                    WorkflowRun b2 = p.getBuildByNumber(2);
                    assertNull(b2);
                }
            }
        });
    }
}
