package org.jenkinsci.plugins.workflow;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

@Issue("JENKINS-46358")
public class CpsStringGroovyMethodsTest {
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Test
    public void eachMatch() throws Exception {
        WorkflowJob j = r.createProject(WorkflowJob.class, "eachMatch");
        j.setDefinition(new CpsFlowDefinition("int c = 0\n" +
                "'foobarfoooobar'.eachMatch('foo') { c++ }\n" +
                "assert c == 2\n", true));
        r.buildAndAssertSuccess(j);
    }

    @Test
    public void find() throws Exception {
        WorkflowJob j = r.createProject(WorkflowJob.class, "find");
        j.setDefinition(new CpsFlowDefinition("assert 'foobar'.find('foo') { it.reverse() } == 'oof'", true));
        r.buildAndAssertSuccess(j);
    }

    @Test
    public void findAll() throws Exception {
        WorkflowJob j = r.createProject(WorkflowJob.class, "findAll");
        j.setDefinition(new CpsFlowDefinition("assert 'foobarfoobarfoo'.findAll('foo') {\n" +
                "  it.reverse()\n" +
                "} == ['oof', 'oof', 'oof']\n", true));
        r.buildAndAssertSuccess(j);
    }

    @Test
    public void replaceAll() throws Exception {
        WorkflowJob j = r.createProject(WorkflowJob.class, "replaceAll");
        j.setDefinition(new CpsFlowDefinition("assert 'foobarfoobarfoo'.replaceAll('foo') {\n" +
                "  it.reverse()\n" +
                "} == 'oofbaroofbaroof'\n", true));
        r.buildAndAssertSuccess(j);
    }

    @Test
    public void replaceFirst() throws Exception {
        WorkflowJob j = r.createProject(WorkflowJob.class, "replaceFirst");
        j.setDefinition(new CpsFlowDefinition("assert 'foobarfoobarfoo'.replaceFirst('foo') {\n" +
                "  it.reverse()\n" +
                "} == 'oofbarfoobarfoo'\n", true));
        r.buildAndAssertSuccess(j);
    }

    @Test
    public void takeWhile() throws Exception {
        WorkflowJob j = r.createProject(WorkflowJob.class, "takeWhile");
        j.setDefinition(new CpsFlowDefinition("assert 'Groovy'.takeWhile {\n" +
                "  it != 'v'\n" +
                "} == 'Groo'\n", true));
        r.buildAndAssertSuccess(j);
    }
}
