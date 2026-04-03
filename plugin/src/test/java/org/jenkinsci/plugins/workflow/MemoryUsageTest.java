package org.jenkinsci.plugins.workflow;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MemoryAssert;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

public class MemoryUsageTest {
    @Rule
    public JenkinsRule j = new JenkinsRule();

    @After
    public void clearLoaders() {
        LOADERS.clear();
    }

    private static final List<WeakReference<ClassLoader>> LOADERS = new ArrayList<>();

    public static void register(Object o) {
        ClassLoader loader = o.getClass().getClassLoader();
        System.err.println("registering " + o + " from " + loader);
        LOADERS.add(new WeakReference<>(loader));
    }

    @Test
    public void shouldNotLeakSimpleTemplateEngine() throws Exception {
        WorkflowJob project = j.createProject(WorkflowJob.class, "test");
        project.setDefinition(new CpsFlowDefinition("""
            %s.register(this);
            node {
                def text = 'Hello $name'
                def engine = new groovy.text.SimpleTemplateEngine()
                def template = engine.createTemplate(text).make(['name': 'foo'])
                println template.toString()
            }
        """.formatted(MemoryUsageTest.class.getName()), false));
        j.assertBuildStatusSuccess(project.scheduleBuild2(0));

        for (WeakReference<ClassLoader> loader : LOADERS) {
            MemoryAssert.assertGC(loader, false);
        }
    }
}
