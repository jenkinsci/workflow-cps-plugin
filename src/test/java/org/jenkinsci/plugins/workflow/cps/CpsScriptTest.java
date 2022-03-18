package org.jenkinsci.plugins.workflow.cps;

import hudson.model.Result;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;

import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

public class CpsScriptTest {

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

}
