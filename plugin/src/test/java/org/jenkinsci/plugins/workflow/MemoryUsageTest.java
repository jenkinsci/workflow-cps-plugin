package org.jenkinsci.plugins.workflow;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import groovy.lang.MetaClass;
import hudson.model.Computer;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
import org.jvnet.hudson.test.MemoryAssert;

public class MemoryUsageTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logger = new LoggerRule().record(CpsFlowExecution.class, Level.FINER);

    @After
    public void clearLoaders() {
        LOADERS.clear();
    }

    private static final List<WeakReference<ClassLoader>> LOADERS = new ArrayList<>();

    // Extracts the classloader of the compiled script (e.g. SimpleTemplateScript1) from SimpleTemplate.script
    public static void registerSimpleTemplate(Object template) throws Exception {
        Field scriptField = template.getClass().getDeclaredField("script");
        scriptField.setAccessible(true);
        Object script = scriptField.get(template);
        ClassLoader loader = script.getClass().getClassLoader();
        System.err.println("registering " + script.getClass().getName() + " from " + loader + " (parent: "
                + loader.getParent() + ")");
        LOADERS.add(new WeakReference<>(loader));
    }

    // Extracts the classloader from GStringTemplate.template (a Closure compiled by the engine)
    public static void registerGStringTemplate(Object template) throws Exception {
        Field templateField = template.getClass().getDeclaredField("template");
        templateField.setAccessible(true);
        Object closure = templateField.get(template);
        Class<?> ownerClass = closure.getClass();
        ClassLoader loader = ownerClass.getClassLoader();
        System.err.println(
                "registering " + ownerClass.getName() + " from " + loader + " (parent: " + loader.getParent() + ")");
        LOADERS.add(new WeakReference<>(loader));
    }

    public static void register(Object o) {
        ClassLoader loader = o.getClass().getClassLoader();
        System.err.println("registering " + o + " from " + loader);
        LOADERS.add(new WeakReference<>(loader));
    }

    @Test
    public void simpleTemplateEngineLeaksClassLoader() throws Exception {
        Computer.threadPoolForRemoting.submit(() -> {}).get();
        WorkflowJob project = j.createProject(WorkflowJob.class, "test");
        project.setDefinition(new CpsFlowDefinition("""
            def text = 'Hello $name'
            def engine = new groovy.text.SimpleTemplateEngine()
            def template = engine.createTemplate(text)
            %s.registerSimpleTemplate(template)
            echo template.make(['name': 'foo']).toString()
        """.formatted(MemoryUsageTest.class.getName()), false));
        j.assertBuildStatusSuccess(project.scheduleBuild2(0));

        assertFalse("expected at least one registered classloader", LOADERS.isEmpty());
        clearInvocationCaches();
        for (WeakReference<ClassLoader> loader : LOADERS) {
            assertNotNull(
                    "template GroovyClassLoader parented to WebAppClassLoader not reachable from pipeline classloader chain, not cleaned up by cleanUpHeap",
                    loader.get());
        }
    }

    @Test
    public void gstringTemplateEngineLeaksClassLoader() throws Exception {
        Computer.threadPoolForRemoting.submit(() -> {}).get();
        WorkflowJob project = j.createProject(WorkflowJob.class, "test");
        project.setDefinition(new CpsFlowDefinition("""
            def text = 'Hello $name'
            def engine = new groovy.text.GStringTemplateEngine()
            def template = engine.createTemplate(text)
            %s.registerGStringTemplate(template)
            echo template.make(['name': 'foo']).toString()
        """.formatted(MemoryUsageTest.class.getName()), false));
        j.assertBuildStatusSuccess(project.scheduleBuild2(0));

        assertFalse("expected at least one registered classloader", LOADERS.isEmpty());
        clearInvocationCaches();
        for (WeakReference<ClassLoader> loader : LOADERS) {
            assertNotNull(
                    "template GroovyClassLoader parented to WebAppClassLoader not reachable from pipeline classloader chain, not cleaned up by cleanUpHeap",
                    loader.get());
        }
    }

    // baseline: pipeline classloader itself is released, leak is specific to template engines
    @Test
    public void pipelineClassLoaderItselfIsReleased() throws Exception {
        Computer.threadPoolForRemoting.submit(() -> {}).get();
        WorkflowJob project = j.createProject(WorkflowJob.class, "test");
        project.setDefinition(new CpsFlowDefinition("""
            %s.register(this)
            def text = 'Hello $name'
            def engine = new groovy.text.SimpleTemplateEngine()
            def template = engine.createTemplate(text)
            echo template.make(['name': 'foo']).toString()
        """.formatted(MemoryUsageTest.class.getName()), false));
        j.assertBuildStatusSuccess(project.scheduleBuild2(0));

        assertFalse("expected at least one registered classloader", LOADERS.isEmpty());
        clearInvocationCaches();
        for (WeakReference<ClassLoader> loader : LOADERS) {
            MemoryAssert.assertGC(loader, false);
        }
    }

    // template engine classloader parent chain does not include the pipeline classloader
    @Test
    public void templateEngineClassLoaderParentIsNotPipelineClassLoader() throws Exception {
        Computer.threadPoolForRemoting.submit(() -> {}).get();
        WorkflowJob project = j.createProject(WorkflowJob.class, "test");
        project.setDefinition(new CpsFlowDefinition("""
            %1$s.register(this)
            def text = 'Hello $name'
            def engine = new groovy.text.SimpleTemplateEngine()
            def template = engine.createTemplate(text)
            %1$s.registerSimpleTemplate(template)
            echo template.make(['name': 'foo']).toString()
        """.formatted(MemoryUsageTest.class.getName()), false));
        j.assertBuildStatusSuccess(project.scheduleBuild2(0));

        assertTrue("expected two registered classloaders", LOADERS.size() >= 2);
        ClassLoader pipelineLoader = LOADERS.get(0).get();
        ClassLoader templateLoader = LOADERS.get(1).get();
        assertNotNull("pipeline classloader should still be alive during test", pipelineLoader);
        assertNotNull("template classloader should still be alive during test", templateLoader);
        ClassLoader parent = templateLoader.getParent();
        boolean foundPipelineLoader = false;
        while (parent != null) {
            if (parent == pipelineLoader) {
                foundPipelineLoader = true;
                break;
            }
            parent = parent.getParent();
        }
        assertFalse(
                "template engine GroovyClassLoader is not a child of the pipeline classloader, so cleanUpHeap never reaches it",
                foundPipelineLoader);
    }

    private static void clearInvocationCaches() throws Exception {
        MetaClass metaClass = ClassInfo.getClassInfo(MemoryUsageTest.class).getMetaClass();
        Method clearInvocationCaches = metaClass.getClass().getDeclaredMethod("clearInvocationCaches");
        clearInvocationCaches.setAccessible(true);
        clearInvocationCaches.invoke(metaClass);
    }
}
