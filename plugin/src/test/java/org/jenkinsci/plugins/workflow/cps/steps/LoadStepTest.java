package org.jenkinsci.plugins.workflow.cps.steps;

import hudson.model.Result;
import java.io.NotSerializableException;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.jenkinsci.plugins.workflow.cps.CpsCompilationErrorsException;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class LoadStepTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Test
    public void basics() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile text: 'println(21*2)', file: 'test.groovy'
                  println 'something printed' // make sure that 'println' in groovy script works
                  load 'test.groovy'
                }""", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("something printed", b);
        r.assertLogContains("42", b);
    }

    /**
     * "evaluate" call is supposed to yield a value
     */
    @Test
    public void evaluationResult() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile text: '21*2', file: 'test.groovy'
                  def o = load('test.groovy')
                  println 'output=' + o
                }""", false));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("output=42", b);
    }

    @Test
    public void compilationErrorsCanBeSerialized() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class);
        p.setDefinition(new CpsFlowDefinition("""
                node {
                  writeFile text: 'bad, syntax', file: 'test.groovy'
                  try {
                    load('test.groovy')
                  } catch (e) {
                    sleep(time: 1, unit: 'MILLISECONDS') // Force the exception to be persisted.
                    throw e
                  }
                }""", true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogNotContains(NotSerializableException.class.getName(), b);
        r.assertLogNotContains(MultipleCompilationErrorsException.class.getName(), b);
        r.assertLogContains(CpsCompilationErrorsException.class.getName(), b);
        r.assertLogContains("unexpected token: bad", b);
    }
}
