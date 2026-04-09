package org.jenkinsci.plugins.workflow;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import groovy.lang.GroovyClassLoader;
import hudson.model.Computer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import jenkins.plugins.git.GitSCMSource;
import jenkins.plugins.git.GitSampleRepoRule;
import org.codehaus.groovy.reflection.ClassInfo;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries;
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration;
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Ignore;
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
    public GitSampleRepoRule sampleRepo = new GitSampleRepoRule();

    @Rule
    public LoggerRule logger = new LoggerRule().record(CpsFlowExecution.class, Level.FINER);

    private static final List<ClassLoader> LOADERS = new ArrayList<>();

    @After
    public void clearLoaders() {
        LOADERS.clear();
    }

    // Captures the template engine's GroovyClassLoader from a compiled SimpleTemplateEngine template.
    // Called from pipeline script via reflection.
    public static void registerSimpleTemplate(Object template) throws Exception {
        var scriptField = template.getClass().getDeclaredField("script");
        scriptField.setAccessible(true);
        Object script = scriptField.get(template);
        // Materialize ClassInfo soft-reference chain as in production
        ClassInfo.getClassInfo(script.getClass()).getCachedClass();
        for (ClassLoader loader = script.getClass().getClassLoader();
                loader instanceof GroovyClassLoader;
                loader = loader.getParent()) {
            LOADERS.add(loader);
        }
    }

    // Captures the template engine's GroovyClassLoader from a compiled GStringTemplateEngine template.
    // Called from pipeline script via reflection.
    public static void registerGStringTemplate(Object template) throws Exception {
        var templateField = template.getClass().getDeclaredField("template");
        templateField.setAccessible(true);
        Object closure = templateField.get(template);
        ClassInfo.getClassInfo(closure.getClass()).getCachedClass();
        for (ClassLoader loader = closure.getClass().getClassLoader();
                loader instanceof GroovyClassLoader;
                loader = loader.getParent()) {
            LOADERS.add(loader);
        }
    }

    public static void register(Object o) {
        LOADERS.add(o.getClass().getClassLoader());
    }

    // Reproduces the production scenario: a shared library vars step creates a
    // SimpleTemplateEngine and calls createTemplate() per invocation
    // while the pipeline calls the step in a loop
    // Each createTemplate() compiles a new SimpleTemplateScript class into the
    // template engines GroovyClassLoader.classCache. Since this classloader is parented
    // to the Jenkins core classloader (not the pipeline classloader), cleanUpHeap never
    // walks into it and never calls clearCache()
    @Ignore("Fails without fix: cleanUpHeap does not reach template engine classloaders")
    @Test
    public void sharedLibrarySimpleTemplateEngineLoaderCleaned() throws Exception {
        Computer.threadPoolForRemoting.submit(() -> {}).get();
        sampleRepo.init();
        sampleRepo.write(
                "vars/simpleTemplate.groovy",
                """
                def call(templateStr, binding) {
                    def engine = new groovy.text.SimpleTemplateEngine()
                    def template = engine.createTemplate(templateStr).make(binding)
                    %s.registerSimpleTemplate(engine.createTemplate(templateStr))
                    return template.toString()
                }
                """
                        .formatted(TemplateEngineMemoryTest.class.getName()));
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get()
                .setLibraries(List.of(new LibraryConfiguration(
                        "testLib", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())))));
        WorkflowJob project = j.createProject(WorkflowJob.class, "test");
        project.setDefinition(new CpsFlowDefinition(
                """
                @Library('testLib@master') _
                def defaultTemplateStr = 'Hello ${name}!'
                for (int i = 0; i < 500; i++) {
                    println simpleTemplate(defaultTemplateStr, ["name": "Leak" + i])
                }
                """,
                true));
        j.assertBuildStatusSuccess(project.scheduleBuild2(0));

        assertFalse("expected at least one registered classloader", LOADERS.isEmpty());
        GroovyClassLoader templateGCL = findTemplateEngineGCL();
        assertNotNull("expected to find the template engine's GroovyClassLoader", templateGCL);
        assertFalse(
                "cleanUpHeap should have cleared SimpleTemplateScript classes from the cache, "
                        + "but found: " + Arrays.toString(templateGCL.getLoadedClasses()),
                Arrays.stream(templateGCL.getLoadedClasses())
                        .anyMatch(c -> c.getName().startsWith("SimpleTemplateScript")));
    }

    // Same as sharedLibrarySimpleTemplateEngineLoaderCleaned but for GStringTemplateEngine.
    @Ignore("Fails without fix: cleanUpHeap does not reach template engine classloaders")
    @Test
    public void sharedLibraryGStringTemplateEngineLoaderCleaned() throws Exception {
        Computer.threadPoolForRemoting.submit(() -> {}).get();
        sampleRepo.init();
        sampleRepo.write(
                "vars/gstringTemplate.groovy",
                """
                def call(templateStr, binding) {
                    def engine = new groovy.text.GStringTemplateEngine()
                    def template = engine.createTemplate(templateStr).make(binding)
                    %s.registerGStringTemplate(engine.createTemplate(templateStr))
                    return template.toString()
                }
                """
                        .formatted(TemplateEngineMemoryTest.class.getName()));
        sampleRepo.git("add", "vars");
        sampleRepo.git("commit", "--message=init");
        GlobalLibraries.get()
                .setLibraries(List.of(new LibraryConfiguration(
                        "testLib", new SCMSourceRetriever(new GitSCMSource(sampleRepo.toString())))));
        WorkflowJob project = j.createProject(WorkflowJob.class, "test");
        project.setDefinition(new CpsFlowDefinition(
                """
                @Library('testLib@master') _
                def defaultTemplateStr = 'Hello ${name}!'
                for (int i = 0; i < 500; i++) {
                    println gstringTemplate(defaultTemplateStr, ["name": "Leak" + i])
                }
                """,
                true));
        j.assertBuildStatusSuccess(project.scheduleBuild2(0));

        assertFalse("expected at least one registered classloader", LOADERS.isEmpty());
        GroovyClassLoader templateGCL = findTemplateEngineGCL();
        assertNotNull("expected to find the template engine's GroovyClassLoader", templateGCL);
        assertFalse(
                "cleanUpHeap should have cleared GStringTemplateScript classes from the cache, "
                        + "but found: " + Arrays.toString(templateGCL.getLoadedClasses()),
                Arrays.stream(templateGCL.getLoadedClasses())
                        .anyMatch(c -> c.getName().contains("GStringTemplateScript")));
    }

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

        assertTrue("expected at least two registered classloaders", LOADERS.size() >= 2);
        ClassLoader pipelineLoader = LOADERS.get(0);
        ClassLoader templateLoader = LOADERS.get(1);
        boolean foundPipelineLoader = false;
        for (ClassLoader parent = templateLoader.getParent(); parent != null; parent = parent.getParent()) {
            if (parent == pipelineLoader) {
                foundPipelineLoader = true;
                break;
            }
        }
        assertFalse(
                "template engine GroovyClassLoader should not be a child of the pipeline classloader",
                foundPipelineLoader);
    }

    private static GroovyClassLoader findTemplateEngineGCL() {
        for (ClassLoader loader : LOADERS) {
            if (loader instanceof GroovyClassLoader && !(loader instanceof GroovyClassLoader.InnerLoader)) {
                return (GroovyClassLoader) loader;
            }
        }
        return null;
    }
}
