package org.jenkinsci.plugins.workflow.cps;

import hudson.model.Result;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;

import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class CpsScriptTest {

    @ClassRule public static BuildWatcher watcher = new BuildWatcher();
    @ClassRule public static JenkinsRule r = new JenkinsRule();

    /**
     * Test the 'evaluate' method call.
     * The first test case.
     */
    @Test
    public void evaluate() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("assert evaluate('1+2+3')==6", true));
        r.buildAndAssertSuccess(p);
    }

    /**
     * The code getting evaluated must also get sandbox transformed.
     */
    @Test
    public void evaluateShallSandbox() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("evaluate('Jenkins.getInstance()')", true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        // execution should have failed with error, pointing that Jenkins.getInstance() is not allowed from sandbox
        r.assertLogContains("org.jenkinsci.plugins.scriptsecurity.sandbox.RejectedAccessException: Scripts not permitted to use staticMethod jenkins.model.Jenkins getInstance", b);
    }

    /** Need to be careful that internal method names in {@link CpsScript} are not likely identifiers in user scripts. */
    @Test public void methodNameClash() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("def build() {20}; def initialize() {10}; def env() {10}; def getShell() {2}; assert build() + initialize() + env() + shell == 42", true));
        r.buildAndAssertSuccess(p);
    }

    @Issue("JENKINS-38167")
    @Test public void bindingDuringConstructor() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("@groovy.transform.Field def opt = (binding.hasVariable('opt')) ? opt : 'default'", true));
        r.buildAndAssertSuccess(p);
    }

    @Issue("SECURITY-2428")
    @Test public void blockImplicitCastingInEvaluate() throws Exception {
        AtomicInteger counter = new AtomicInteger();
        BiFunction<String, String, String> embeddedScript = (decl, main) -> "" +
                "class Test" + counter.incrementAndGet() + " {\\n" +
                "  " + decl + "\\n" +
                "  Object map\\n" +
                "  @NonCPS public void main(String[] args) { " + main + " }\\n" +
                "}\\n";
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition(
                "list = ['secret.key']\n" +
                "map = [:]\n" +
                "evaluate('" + embeddedScript.apply("File list", "map.file = list") + "')\n" +
                "file = map.file\n" +
                "evaluate('" + embeddedScript.apply("String[] file", "map.lines = file") + "')\n" +
                "for (String line in map.lines) { echo(line) }\n", true));
        WorkflowRun b = r.buildAndAssertStatus(Result.FAILURE, p);
        r.assertLogContains("Scripts not permitted to use new java.io.File java.lang.String", b);
    }

    @Issue("JENKINS-73031")
    @Test public void staticInterfaceMethod() throws Exception {
        var p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("def x = List.of(1, 2, 3); echo(/x=$x/)", false));
        var b = r.buildAndAssertStatus(Result.FAILURE, p);
        r.assertLogContains("JENKINS-73031", b);
    }

    @Test public void blockRun() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("run(null, ['test'] as String[])\n", true));
        WorkflowRun b = r.buildAndAssertStatus(Result.FAILURE, p);
        // The existence of CpsScript.run leads me to believe that it was intended to be allowed by CpsWhitelist, but
        // that is not currently the case, and I see no reason to start allowing it at this point.
        r.assertLogContains("Scripts not permitted to use method groovy.lang.Script run java.io.File java.lang.String[]", b);
    }

}
