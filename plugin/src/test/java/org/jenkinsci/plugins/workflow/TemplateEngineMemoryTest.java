package org.jenkinsci.plugins.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyArray;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import groovy.lang.GroovyClassLoader;
import hudson.model.Computer;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.codehaus.groovy.reflection.ClassInfo;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class TemplateEngineMemoryTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logger = new LoggerRule().record(CpsFlowExecution.class, Level.FINER);

    private static final List<GroovyClassLoader> TEMPLATE_LOADERS = new ArrayList<>();

    @After
    public void clearLoaders() {
        TEMPLATE_LOADERS.clear();
    }

    // Called from pipeline with the template's compiled script object.
    // Captures the template engine's GroovyClassLoader (walking past
    // InnerLoaders) so the test can verify cleanUpHeap cleared its cache.
    // Materializes ClassInfo soft-reference chain as in production.
    public static void register(Object scriptObject) {
        ClassInfo.getClassInfo(scriptObject.getClass()).getCachedClass();
        ClassLoader loader = scriptObject.getClass().getClassLoader();
        while (loader instanceof GroovyClassLoader.InnerLoader) {
            loader = loader.getParent();
        }
        if (loader instanceof GroovyClassLoader gcl) {
            TEMPLATE_LOADERS.add(gcl);
        }
    }

    @Test
    public void simpleTemplateEngineLoaderCleaned() throws Exception {
        Computer.threadPoolForRemoting.submit(() -> {}).get();
        WorkflowJob p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                def engine = new groovy.text.SimpleTemplateEngine()
                def template = engine.createTemplate('Hello $name')
                def scriptField = template.getClass().getDeclaredField('script')
                scriptField.setAccessible(true)
                %s.register(scriptField.get(template))
                echo template.make([name: 'world']).toString()
                """.formatted(TemplateEngineMemoryTest.class.getName()), false));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertFalse("expected at least one registered loader", TEMPLATE_LOADERS.isEmpty());
        GroovyClassLoader gcl = TEMPLATE_LOADERS.get(0);
        assertNotNull(gcl);
        assertThat(
                "cleanUpHeap should have cleared SimpleTemplateScript classes from the template engine's cache",
                gcl.getLoadedClasses(), emptyArray());
    }

    @Test
    public void gstringTemplateEngineLoaderCleaned() throws Exception {
        Computer.threadPoolForRemoting.submit(() -> {}).get();
        WorkflowJob p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
                """
                def engine = new groovy.text.GStringTemplateEngine()
                def template = engine.createTemplate('Hello $name')
                def templateField = template.getClass().getDeclaredField('template')
                templateField.setAccessible(true)
                %s.register(templateField.get(template))
                echo template.make([name: 'world']).toString()
                """.formatted(TemplateEngineMemoryTest.class.getName()), false));
        j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertFalse("expected at least one registered loader", TEMPLATE_LOADERS.isEmpty());
        GroovyClassLoader gcl = TEMPLATE_LOADERS.get(0);
        assertNotNull(gcl);
        assertThat(
                "cleanUpHeap should have cleared GStringTemplateScript classes from the template engine's cache",
                gcl.getLoadedClasses(), emptyArray());
    }
}
