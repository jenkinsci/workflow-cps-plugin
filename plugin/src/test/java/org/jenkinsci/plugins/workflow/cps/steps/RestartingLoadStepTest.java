package org.jenkinsci.plugins.workflow.cps.steps;

import javax.annotation.CheckForNull;
import javax.inject.Inject;
import groovy.lang.GroovyShell;
import hudson.FilePath;
import hudson.model.Result;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.cps.GroovyShellDecorator;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class RestartingLoadStepTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    @Inject
    Jenkins jenkins;

    private static final String EXISTING_VAR_NAME = "INJECTED_VAR";
    private static final String EXISTING_VAR_VALUE = "PRE_EXISTING";

    /**
     * Makes sure that loaded scripts survive persistence.
     */
    @Test
    public void persistenceOfLoadedScripts() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");
                jenkins.getWorkspaceFor(p).child("test.groovy").write(
                    "def answer(i) { return i*2; }\n" +
                    "def foo(body) {\n" +
                    "    def i = body()\n" +
                    "    semaphore 'watchA'\n" +
                    "    return answer(i);\n" +
                    "}\n" +
                    "return this;", null);
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  println 'started'\n" +
                    "  def o = load 'test.groovy'\n" +
                    "  println 'o=' + o.foo({21})\n" +
                    "}", false));

                // get the build going
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();

                // wait until the executor gets assigned and the execution pauses
                SemaphoreStep.waitForStart("watchA/1", b);

                assertTrue(JenkinsRule.getLog(b), b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);

                // resume from where it left off
                SemaphoreStep.success("watchA/1", null);

                story.j.waitForCompletion(b);
                story.j.assertBuildStatusSuccess(b);

                story.j.assertLogContains("o=42", b);
            }
        });
    }

    /**
     * The load command itself can block while it executes the script
     */
    @Test
    public void pauseInsideLoad() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");
                jenkins.getWorkspaceFor(p).child("test.groovy").write(
                    "def answer(i) { return i*2; }\n" +
                    "def i=21;\n" +
                    "semaphore 'watchB'\n" +
                    "return answer(i);\n", null);
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  println 'started'\n" +
                    "  def o = load 'test.groovy'\n" +
                    "  println 'o=' + o;\n" +
                    "}", false));

                // get the build going
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();

                // wait until the executor gets assigned and the execution pauses
                SemaphoreStep.waitForStart("watchB/1", b);

                assertTrue(JenkinsRule.getLog(b), b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);

                // resume from where it left off
                SemaphoreStep.success("watchB/1", null);

                story.j.waitForCompletion(b);
                story.j.assertBuildStatusSuccess(b);

                story.j.assertLogContains("o=42", b);
            }
        });
    }

    @Issue("JENKINS-36372")
    @Test public void accessToSiblingScripts() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.createProject(WorkflowJob.class, "p");
                jenkins.getWorkspaceFor(p).child("a.groovy").write("def call(arg) {echo \"a ran on ${arg}\"}; this", null);
                ScriptApproval.get().approveSignature("method groovy.lang.Binding getVariables");
                jenkins.getWorkspaceFor(p).child("b.groovy").write("def m(arg) {echo \"${this} binding=${binding.variables}\"; a(\"${arg} from b\")}; this", null);
                // Control case:
                p.setDefinition(new CpsFlowDefinition("a = 0; node {a = load 'a.groovy'}; def b; node {b = load 'b.groovy'}; echo \"${this} binding=${binding.variables}\"; b.m('value')", true));
                story.j.assertLogContains("a ran on value from b", story.j.assertBuildStatusSuccess(p.scheduleBuild2(0)));
                // Test case:
                p.setDefinition(new CpsFlowDefinition("a = 0; node {a = load 'a.groovy'}; semaphore 'wait'; def b; node {b = load 'b.groovy'}; echo \"${this} binding=${binding.variables}\"; b.m('value')", true));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(2);
                SemaphoreStep.success("wait/1", null);
                story.j.assertLogContains("a ran on value from b", story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b)));
                // Better case:
                jenkins.getWorkspaceFor(p).child("b.groovy").write("def m(a, arg) {a(\"${arg} from b\")}; this", null);
                p.setDefinition(new CpsFlowDefinition("def a; def b; node {a = load 'a.groovy'; b = load 'b.groovy'}; b.m(a, 'value')", true));
                story.j.assertLogContains("a ran on value from b", story.j.assertBuildStatusSuccess(p.scheduleBuild2(0)));
            }
        });
    }

    @Issue("JENKINS-50172")
    @Test public void loadAndUnnamedClassesInPackage() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                FilePath pkgDir = story.j.jenkins.getWorkspaceFor(p).child("src/org/foo/devops");
                pkgDir.mkdirs();
                pkgDir.child("Utility.groovy").write("package org.foo.devops\n" +
                        "def isValueExist(String outerValue) {\n" +
                        "  return new Object() {\n" +
                        "    def isValueExist(String value) {\n" +
                        "        if(value == null || value.trim().length() == 0 || value.trim().equals(\"\\\"\\\"\")) {\n" +
                        "            return false\n" +
                        "        }\n" +
                        "        return true\n" +
                        "    }\n" +
                        "  }.isValueExist(outerValue)\n" +
                        "}\n" +
                        "return this;\n", null);
                pkgDir.child("JenkinsEnvironment.groovy").write("package org.foo.devops\n" +
                        "class InnerEnvClass {\n" +
                        "  def loadProdConfiguration() {\n" +
                        "      def valueMap = [:]\n" +
                        "      valueMap.put('key','value')\n" +
                        "      return valueMap\n" +
                        "  }\n" +
                        "}\n" +
                        "def loadProdConfiguration() {\n" +
                        "  return new InnerEnvClass().loadProdConfiguration()\n" +
                        "}\n" +
                        "return this;\n", null);

                p.setDefinition(new CpsFlowDefinition("def util\n" +
                        "def config\n" +
                        "def util2\n" +
                        "node('master') {\n" +
                        "    config = load 'src/org/foo/devops/JenkinsEnvironment.groovy'\n" +
                        "    util = load 'src/org/foo/devops/Utility.groovy'\n" +
                        "    config.loadProdConfiguration()\n" +
                        "}\n" +
                        "util.isValueExist(\"\")\n" +
                        "semaphore 'wait'\n" +
                        "node('master') {\n" +
                        "    util2 = load 'src/org/foo/devops/Utility.groovy'\n" +
                        "    util = load 'src/org/foo/devops/Utility.groovy'\n" +
                        "    assert util.isValueExist('foo') == true\n" +
                        "    assert util2.isValueExist('foo') == true\n" +
                        "}\n", true));
                WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("wait/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                SemaphoreStep.success("wait/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
            }
        });
    }

    @TestExtension(value={"existingBindingsOnRestart", "existingBindingsWithLoadOnRestart"})
    public static class InjectedVariable extends GroovyShellDecorator {

        @Override
        public void configureShell(@CheckForNull CpsFlowExecution context, GroovyShell shell) {
            shell.setVariable(EXISTING_VAR_NAME, EXISTING_VAR_VALUE);
        }
    }

    @Test
    public void existingBindingsOnRestart() throws Exception {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p1");
            p.setDefinition(new CpsFlowDefinition(
                    "echo(/Pre-semaphore value is ${" + EXISTING_VAR_NAME + "}/)\n" +
                    "semaphore('wait')\n" +
                    "echo(/Post-semaphore value is ${" + EXISTING_VAR_NAME + "}/)", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            r.assertLogContains("Pre-semaphore value is " + EXISTING_VAR_VALUE, b);
        });
        story.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p1", WorkflowJob.class);
            WorkflowRun b = p.getBuildByNumber(1);
            SemaphoreStep.success("wait/1", null);
            r.waitForCompletion(b);
            r.assertBuildStatus(Result.SUCCESS, b);
            r.assertLogContains("Post-semaphore value is " + EXISTING_VAR_VALUE, b);
        });
    }

    @Test
    public void existingBindingsWithLoadOnRestart() {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            r.jenkins.getWorkspaceFor(p).child("a.groovy").write(
                    "def call(arg) {echo (/a ran on ${arg}/)}; this", null);
            ScriptApproval.get().approveSignature("method groovy.lang.Binding getVariables");
            r.jenkins.getWorkspaceFor(p).child("b.groovy").write(
                    "def m(arg) {echo (/${this} binding=${binding.variables}/); a(/${arg} from b/)}; this", null);
            p.setDefinition(new CpsFlowDefinition(
                    "a = 0;" +
                    "node {a = load 'a.groovy'};" +
                    "echo (/Pre-semaphore value is ${" + EXISTING_VAR_NAME + "}/);" +
                    "semaphore 'wait';" +
                    "echo (/Post-semaphore value is ${" + EXISTING_VAR_NAME + "}/);" +
                    "def b;" +
                    "node {b = load 'b.groovy'};" +
                    "echo (/${this} binding=${binding.variables}/);" +
                    "b.m('value')", true));
            WorkflowRun b = p.scheduleBuild2(0).getStartCondition().get();
            SemaphoreStep.waitForStart("wait/1", b);
            r.assertLogContains("Pre-semaphore value is " + EXISTING_VAR_VALUE, b);
        });
        story.then( r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getBuildByNumber(1);
            SemaphoreStep.success("wait/1", null);
            r.waitForCompletion(b);
            r.assertBuildStatus(Result.SUCCESS, b);
            r.assertLogContains("Post-semaphore value is " + EXISTING_VAR_VALUE, b);
            r.assertLogContains("a ran on value from b", b);
        });
    }

    @Test
    public void updatedBindingsOnRestart() throws Exception {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
            r.jenkins.getWorkspaceFor(p).child("a.groovy").write("tmp = 'tmp'; { -> tmp}", null);
            p.setDefinition(new CpsFlowDefinition(
                    "node() {\n" +
                    "  a = load('a.groovy')\n" +
                    "}\n" +
                    "echo(/before change: ${a()}/)\n" +
                    "tmp = 'tmp2'\n" +
                    "echo(/before restart: ${a()}/)\n" +
                    "semaphore('wait')\n" +
                    "tmp = 'tmp3'\n" +
                    "echo(/after restart: ${a()}/)\n", true));
            WorkflowRun b = p.scheduleBuild2(0).waitForStart();
            SemaphoreStep.waitForStart("wait/1", b);
            r.assertLogContains("before change: tmp", b);
            r.assertLogContains("before restart: tmp2", b);
        });
        story.then(r -> {
            WorkflowJob p = r.jenkins.getItemByFullName("p", WorkflowJob.class);
            WorkflowRun b = p.getBuildByNumber(1);
            SemaphoreStep.success("wait/1", null);
            r.assertBuildStatusSuccess(r.waitForCompletion(b));
            r.assertLogContains("after restart: tmp3", b);
        });
    }
}
