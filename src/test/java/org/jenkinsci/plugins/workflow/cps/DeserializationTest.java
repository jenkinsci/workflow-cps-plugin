package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.GroovyShell;
import hudson.model.Result;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.RestartableJenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import javax.annotation.CheckForNull;

public class DeserializationTest {
    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public RestartableJenkinsRule story = new RestartableJenkinsRule();

    private static final String EXISTING_VAR_NAME = "INJECTED_VAR";
    private static final String EXISTING_VAR_VALUE = "PRE_EXISTING";
    private static final String A_GROOVY = "a.groovy";
    private static final String A_CONTENTS = "def call(arg) {echo \"a ran on ${arg}\"}; this";
    private static final String B_GROOVY = "b.groovy";
    private static final String B_CONTENTS = "def m(arg) {" +
                                             "echo \"${this} binding=${binding.variables}\";" +
                                             "a(\"${arg} from b\")}; this";
    private static String LOAD_PREFIX = "a = 0;" +
                                        "node {a = load 'a.groovy'};" +
                                        "echo \"Pre-semaphore value is ${" + EXISTING_VAR_NAME + "}\";";
    private static String LOAD_SEMAPHORE = "semaphore 'wait';";
    private static String LOAD_SUFFIX = "echo \"Post-semaphore value is ${" + EXISTING_VAR_NAME + "}\";" +
                                        "def b;" +
                                        "node {b = load 'b.groovy'};" +
                                        "echo \"${this} binding=${binding.variables}\";" +
                                        "b.m('value')";


    @Test
    public void existingVariablesOnRestart() throws Exception {
        story.then(r -> {
            WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p1");
            p.setDefinition(new CpsFlowDefinition("echo(\"Pre-semaphore value is ${" + EXISTING_VAR_NAME + "}\")\n" +
                                                  "semaphore('wait')\n" +
                                                  "echo(\"Post-semaphore value is ${" + EXISTING_VAR_NAME + "}\")", true));
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
    public void loadWithExistingVariables() {
        // Testing with variables also injected via load step
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                story.j.jenkins.getWorkspaceFor(p).child(A_GROOVY).write(A_CONTENTS, null);
                ScriptApproval.get().approveSignature("method groovy.lang.Binding getVariables");
                story.j.jenkins.getWorkspaceFor(p).child(B_GROOVY).write(B_CONTENTS, null);
                // Control case:
                p.setDefinition(new CpsFlowDefinition(LOAD_PREFIX + LOAD_SUFFIX, true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("a ran on value from b", b);
                story.j.assertLogContains("Pre-semaphore value is " + EXISTING_VAR_VALUE, b);
                // Test case:
                p.setDefinition(new CpsFlowDefinition(LOAD_PREFIX + LOAD_SEMAPHORE + LOAD_SUFFIX, true));
                b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("wait/1", b);
                story.j.assertLogContains("Pre-semaphore value is " + EXISTING_VAR_VALUE, b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                SemaphoreStep.success("wait/1", null);
                story.j.assertLogContains("a ran on value from b", story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b)));
                story.j.assertLogContains("Post-semaphore value is " + EXISTING_VAR_VALUE, b);
            }
        });
    }

    @Test
    public void clashingVariablesOnRestart() throws Exception {
        String clashingValue = "0";
        String sameVariable = EXISTING_VAR_NAME + " = " + clashingValue + ";";
        // Testing with identical variable names injected via load step
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.createProject(WorkflowJob.class, "p");
                story.j.jenkins.getWorkspaceFor(p).child(A_GROOVY).write(A_CONTENTS, null);
                ScriptApproval.get().approveSignature("method groovy.lang.Binding getVariables");
                story.j.jenkins.getWorkspaceFor(p).child(B_GROOVY).write(B_CONTENTS, null);
                // Control case:
                p.setDefinition(new CpsFlowDefinition(sameVariable + LOAD_PREFIX + LOAD_SUFFIX, true));
                WorkflowRun b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("a ran on value from b", b);
                story.j.assertLogContains("Pre-semaphore value is " + clashingValue, b);
                // Test case:
                p.setDefinition(new CpsFlowDefinition(sameVariable + LOAD_PREFIX + LOAD_SEMAPHORE + LOAD_SUFFIX, true));
                b = p.scheduleBuild2(0).getStartCondition().get();
                SemaphoreStep.waitForStart("wait/1", b);
                story.j.assertLogContains("Pre-semaphore value is " + clashingValue, b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                WorkflowJob p = story.j.jenkins.getItemByFullName("p", WorkflowJob.class);
                WorkflowRun b = p.getBuildByNumber(1);
                SemaphoreStep.success("wait/1", null);
                story.j.assertLogContains("a ran on value from b", story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b)));
                story.j.assertLogContains("Post-semaphore value is " + clashingValue, b);
            }
        });
    }

    @TestExtension
    public static class InjectedVariable extends GroovyShellDecorator {

        @Override
        public void configureShell(@CheckForNull CpsFlowExecution context, GroovyShell shell) {
            shell.setVariable(EXISTING_VAR_NAME, EXISTING_VAR_VALUE);
        }
    }
}
