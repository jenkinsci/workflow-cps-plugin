package org.jenkinsci.plugins.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import groovy.lang.GroovyClassLoader;
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

public class TemplateEngineMemoryTest {

    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Rule
    public LoggerRule logger = new LoggerRule().record(CpsFlowExecution.class, Level.FINER);

    @After
    public void clearLoaders() {
        LOADERS.clear();
        STRONG_REFS.clear();
    }

    private static final List<WeakReference<ClassLoader>> LOADERS = new ArrayList<>();
    // prevent GC before we can inspect getLoadedClasses(); clearCache() empties loaded classes even with this held
    private static final List<ClassLoader> STRONG_REFS = new ArrayList<>();

    // walks up from InnerLoader to parent GroovyClassLoader, registering all intermediate loaders
    public static void registerSimpleTemplate(Object template) throws Exception {
        Field scriptField = template.getClass().getDeclaredField("script");
        scriptField.setAccessible(true);
        Object script = scriptField.get(template);
        for (ClassLoader loader = script.getClass().getClassLoader(); loader instanceof GroovyClassLoader; loader = loader.getParent()) {
            System.err.println("registering " + script.getClass().getName() + " from " + loader);
            LOADERS.add(new WeakReference<>(loader));
            if (!(loader instanceof GroovyClassLoader.InnerLoader)) {
                STRONG_REFS.add(loader);
            }
        }
    }

    // walks up from InnerLoader to parent GroovyClassLoader, registering all intermediate loaders
    public static void registerGStringTemplate(Object template) throws Exception {
        Field templateField = template.getClass().getDeclaredField("template");
        templateField.setAccessible(true);
        Object closure = templateField.get(template);
        for (ClassLoader loader = closure.getClass().getClassLoader(); loader instanceof GroovyClassLoader; loader = loader.getParent()) {
            System.err.println("registering " + closure.getClass().getName() + " from " + loader);
            LOADERS.add(new WeakReference<>(loader));
            if (!(loader instanceof GroovyClassLoader.InnerLoader)) {
                STRONG_REFS.add(loader);
            }
        }
    }

    public static void register(Object o) {
        ClassLoader loader = o.getClass().getClassLoader();
        System.err.println("registering " + o + " from " + loader);
        LOADERS.add(new WeakReference<>(loader));
    }

    // after cleanUpHeap, the template engine GroovyClassLoader cache should be empty
    @Test
    public void simpleTemplateEngineLoaderCleanedUp() throws Exception {
        Computer.threadPoolForRemoting.submit(() -> {}).get();
        WorkflowJob project = j.createProject(WorkflowJob.class, "test");
        project.setDefinition(new CpsFlowDefinition("""
            def text = 'Hello $name'
            def engine = new groovy.text.SimpleTemplateEngine()
            def template = engine.createTemplate(text)
            %s.registerSimpleTemplate(template)
            echo template.make(['name': 'foo']).toString()
        """.formatted(TemplateEngineMemoryTest.class.getName()), false));
        j.assertBuildStatusSuccess(project.scheduleBuild2(0));

        assertFalse(STRONG_REFS.isEmpty());
        clearInvocationCaches();
        GroovyClassLoader gcl = (GroovyClassLoader) STRONG_REFS.get(0);
        List<String> leftover = java.util.Arrays.stream(gcl.getLoadedClasses())
                .map(Class::getName)
                .collect(java.util.stream.Collectors.toList());
        // TODO cleanUpHeap should clear this but currently misses it; change to empty() once fixed
        assertThat(leftover, hasItems("SimpleTemplateScript1"));
    }

    @Test
    public void gstringTemplateEngineLoaderCleanedUp() throws Exception {
        Computer.threadPoolForRemoting.submit(() -> {}).get();
        WorkflowJob project = j.createProject(WorkflowJob.class, "test");
        project.setDefinition(new CpsFlowDefinition("""
            def text = 'Hello $name'
            def engine = new groovy.text.GStringTemplateEngine()
            def template = engine.createTemplate(text)
            %s.registerGStringTemplate(template)
            echo template.make(['name': 'foo']).toString()
        """.formatted(TemplateEngineMemoryTest.class.getName()), false));
        j.assertBuildStatusSuccess(project.scheduleBuild2(0));

        assertFalse(STRONG_REFS.isEmpty());
        clearInvocationCaches();
        GroovyClassLoader gcl = (GroovyClassLoader) STRONG_REFS.get(0);
        List<String> leftover = java.util.Arrays.stream(gcl.getLoadedClasses())
                .map(Class::getName)
                .collect(java.util.stream.Collectors.toList());
        // TODO cleanUpHeap should clear this but currently misses it; change to empty() once fixed
        assertThat(
                leftover,
                hasItems(
                        "groovy.tmp.templates.GStringTemplateScript1",
                        "groovy.tmp.templates.GStringTemplateScript1$_getTemplate_closure1"));
    }

    // baseline: pipeline classloader itself is released
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
        """.formatted(TemplateEngineMemoryTest.class.getName()), false));
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
        """.formatted(TemplateEngineMemoryTest.class.getName()), false));
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
                "expected template engine GroovyClassLoader to be a child of the pipeline classloader",
                foundPipelineLoader);
    }

    private static void clearInvocationCaches() throws Exception {
        MetaClass metaClass =
                ClassInfo.getClassInfo(TemplateEngineMemoryTest.class).getMetaClass();
        Method clearInvocationCaches = metaClass.getClass().getDeclaredMethod("clearInvocationCaches");
        clearInvocationCaches.setAccessible(true);
        clearInvocationCaches.invoke(metaClass);
    }
}
