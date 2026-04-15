package org.jenkinsci.plugins.workflow;

import static org.junit.Assert.assertFalse;

import groovy.lang.GroovyClassLoader;
import groovy.lang.MetaClass;
import hudson.model.Computer;
import java.lang.ref.WeakReference;
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

    private static final List<WeakReference<ClassLoader>> LOADERS = new ArrayList<>();

    @After
    public void clearLoaders() {
        LOADERS.clear();
    }

    public static void register(Object scriptObject) {
        ClassLoader loader = scriptObject.getClass().getClassLoader();
        while (loader instanceof GroovyClassLoader.InnerLoader) {
            loader = loader.getParent();
        }
        LOADERS.add(new WeakReference<>(loader));
    }

    @Test
    public void simpleTemplateEngineLoaderReleased() throws Exception {
        Computer.threadPoolForRemoting.submit(() -> {}).get();
        WorkflowJob p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                def engine = new groovy.text.SimpleTemplateEngine()
                def template = engine.createTemplate('Hello $name')
                def scriptField = template.getClass().getDeclaredField('script')
                scriptField.setAccessible(true)
                %s.register(scriptField.get(template))
                echo template.make([name: 'world']).toString()
                """.formatted(TemplateEngineMemoryTest.class.getName()), false));
        j.buildAndAssertSuccess(p);
        assertFalse(LOADERS.isEmpty());
        clearMetaClassInvocationCaches();
        for (WeakReference<ClassLoader> loaderRef : LOADERS) {
            MemoryAssert.assertGC(loaderRef, false);
        }
    }

    @Test
    public void gstringTemplateEngineLoaderReleased() throws Exception {
        Computer.threadPoolForRemoting.submit(() -> {}).get();
        WorkflowJob p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("""
                def engine = new groovy.text.GStringTemplateEngine()
                def template = engine.createTemplate('Hello $name')
                def templateField = template.getClass().getDeclaredField('template')
                templateField.setAccessible(true)
                %s.register(templateField.get(template))
                echo template.make([name: 'world']).toString()
                """.formatted(TemplateEngineMemoryTest.class.getName()), false));
        j.buildAndAssertSuccess(p);
        assertFalse(LOADERS.isEmpty());
        clearMetaClassInvocationCaches();
        for (WeakReference<ClassLoader> loaderRef : LOADERS) {
            MemoryAssert.assertGC(loaderRef, false);
        }
    }

    private static void clearMetaClassInvocationCaches() throws Exception {
        MetaClass metaClass =
                ClassInfo.getClassInfo(TemplateEngineMemoryTest.class).getMetaClass();
        Method clearInvocationCaches = metaClass.getClass().getDeclaredMethod("clearInvocationCaches");
        clearInvocationCaches.setAccessible(true);
        clearInvocationCaches.invoke(metaClass);
    }
}
