package org.jenkinsci.plugins.workflow;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.junit.Assert.assertFalse;

import groovy.lang.GroovyClassLoader;
import groovy.lang.MetaClass;
import hudson.model.Computer;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.codehaus.groovy.reflection.ClassInfo;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
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
        assertThat(
                "template engine classes should be removed from groovy globalClassValue by cleanUpHeap",
                findLeakedGlobalClassValueEntries(),
                empty());
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
        assertThat(
                "template engine classes should be removed from groovy globalClassValue by cleanUpHeap",
                findLeakedGlobalClassValueEntries(),
                empty());
        clearMetaClassInvocationCaches();
        for (WeakReference<ClassLoader> loaderRef : LOADERS) {
            MemoryAssert.assertGC(loaderRef, false);
        }
    }

    // checks groovy ClassInfo.globalClassValue for template engine class entries
    // this is the data structure that creates the strong reference chain
    // (ClassInfo -> MetaClass -> theClass) preventing GC of template engine classloaders
    // uses the same reflection approach as CpsFlowExecution.cleanUpGlobalClassValue
    // because there is no public api to query globalClassValue without creating entries
    private static Set<String> findLeakedGlobalClassValueEntries() throws Exception {
        Set<String> result = new HashSet<>();
        Field globalClassValueF = ClassInfo.class.getDeclaredField("globalClassValue");
        globalClassValueF.setAccessible(true);
        Object globalClassValue = globalClassValueF.get(null);
        Class<?> preJava7C = Class.forName("org.codehaus.groovy.reflection.GroovyClassValuePreJava7");
        if (!preJava7C.isInstance(globalClassValue)) {
            return result;
        }
        Field mapF = preJava7C.getDeclaredField("map");
        mapF.setAccessible(true);
        Collection<?> entries = (Collection<?>)
                mapF.get(globalClassValue).getClass().getMethod("values").invoke(mapF.get(globalClassValue));
        Field classRefF = ClassInfo.class.getDeclaredField("classRef");
        classRefF.setAccessible(true);
        Method getValueM = Class.forName("org.codehaus.groovy.util.AbstractConcurrentMapBase$Entry")
                .getMethod("getValue");
        for (Object entry : entries) {
            Object ci = getValueM.invoke(entry);
            if (ci == null) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Class<?> clazz = ((WeakReference<Class<?>>) classRefF.get(ci)).get();
            if (clazz == null) {
                continue;
            }
            ClassLoader loader = clazz.getClassLoader();
            while (loader instanceof GroovyClassLoader.InnerLoader) {
                loader = loader.getParent();
            }
            if (loader instanceof GroovyClassLoader && !(loader.getParent() instanceof GroovyClassLoader)) {
                result.add(clazz.getName());
            }
        }
        return result;
    }

    /**
     * verify that cleaning up template engine classes from a completed build
     * does not break a concurrently running build that is still using a template engine
     */
    @Test
    public void concurrentBuildNotAffectedByCleanup() throws Exception {
        Computer.threadPoolForRemoting.submit(() -> {}).get();
        // build b: uses a template engine before and after a semaphore
        // the template is created and consumed within each block to avoid
        // cps serialization issues (SimpleTemplate is not serializable)
        WorkflowJob b = j.createProject(WorkflowJob.class, "b");
        b.setDefinition(new CpsFlowDefinition("""
                echo new groovy.text.SimpleTemplateEngine().createTemplate('Hello $name').make([name: 'before']).toString()
                semaphore 'wait'
                echo new groovy.text.SimpleTemplateEngine().createTemplate('Hello $name').make([name: 'after']).toString()
                """, false));
        var buildB = b.scheduleBuild2(0);
        SemaphoreStep.waitForStart("wait/1", buildB.getStartCondition().get());

        // build a: uses a template engine and completes
        // its cleanUpHeap calls cleanUpTemplateEngineClasses, which scans
        // classinfo globally and cleans entries for all template engine classloaders
        WorkflowJob a = j.createProject(WorkflowJob.class, "a");
        a.setDefinition(new CpsFlowDefinition("""
                def engine = new groovy.text.SimpleTemplateEngine()
                def template = engine.createTemplate('Hello $name')
                echo template.make([name: 'world']).toString()
                """, false));
        j.buildAndAssertSuccess(a);

        // release build b and verify it completes successfully
        // if the cleanup broke build b template engine, this would fail
        SemaphoreStep.success("wait/1", null);
        j.assertBuildStatusSuccess(buildB);
    }

    private static void clearMetaClassInvocationCaches() throws Exception {
        MetaClass metaClass =
                ClassInfo.getClassInfo(TemplateEngineMemoryTest.class).getMetaClass();
        Method clearInvocationCaches = metaClass.getClass().getDeclaredMethod("clearInvocationCaches");
        clearInvocationCaches.setAccessible(true);
        clearInvocationCaches.invoke(metaClass);
    }
}
