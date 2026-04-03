package org.jenkinsci.plugins.workflow;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MemoryAssert;

import java.lang.ref.WeakReference;

public class MemoryUsageTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void shouldNotLeakSimpleTemplateEngine() throws Exception {
        WorkflowJob project = j.createProject(WorkflowJob.class, "test");
        project.setDefinition(new CpsFlowDefinition("""
            def text = 'Hello $name'
            def engine = new groovy.text.SimpleTemplateEngine()
            def template = engine.createTemplate(text).make(['name': 'foo'])
            println template.toString()
        """, false));
        j.assertBuildStatusSuccess(project.scheduleBuild2(0));
        MemoryAssert.assertGC(new WeakReference<>(this.getClass().getClassLoader()), true);
    }
}
