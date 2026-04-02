package org.jenkinsci.plugins.workflow;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MemoryAssert;

public class MemoryUsageTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void shouldNotLeakSimpleTemplateEngine() throws Exception {
        WorkflowJob project = j.createProject(WorkflowJob.class, "test");
        project.setDefinition(new CpsFlowDefinition("""
            def text = 'Hello $name'
            for(int i=0;i<5000;i++) { // TODO change loop upper limit to trigger the memory leak
                def engine = new groovy.text.SimpleTemplateEngine()
                def template = engine.createTemplate(text).make(['name': 'foo ' + i])
                println template.toString()
            }
        """, false));

        int additionalMemory = MemoryAssert.increasedMemory(
                () -> {
                    j.assertBuildStatusSuccess(project.scheduleBuild2(0));
                    return null;
                },
                (obj, referredFrom, reference) -> !obj.getClass().getName().startsWith("SimpleTemplateEngine")
        ).stream().mapToInt(value -> value.byteSize).sum();

        assert additionalMemory == 0;
    }
}
