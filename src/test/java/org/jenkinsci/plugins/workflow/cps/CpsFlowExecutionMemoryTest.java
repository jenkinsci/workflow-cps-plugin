/*
 * The MIT License
 *
 * Copyright 2017 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.MetaClass;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.codehaus.groovy.reflection.ClassInfo;
import org.codehaus.groovy.transform.ASTTransformationVisitor;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.After;
import org.junit.ClassRule;
import org.junit.Test;
import static org.junit.Assert.*;
import org.junit.Ignore;
import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.For;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.MemoryAssert;

@For(CpsFlowExecution.class) // but kept separate from CpsFlowExecutionTest since it is sensitive to at least a leak from trustedShell()
public class CpsFlowExecutionMemoryTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public LoggerRule logger = new LoggerRule().record(CpsFlowExecution.class, Level.FINER);

    @After public void clearLoaders() {
        LOADERS.clear();
    }
    private static final List<WeakReference<ClassLoader>> LOADERS = new ArrayList<>();
    public static void register(Object o) {
        ClassLoader loader = o.getClass().getClassLoader();
        System.err.println("registering " + o + " from " + loader);
        LOADERS.add(new WeakReference<>(loader));
    }

    @Test public void loaderReleased() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        r.jenkins.getWorkspaceFor(p).child("lib.groovy").write(CpsFlowExecutionMemoryTest.class.getName() + ".register(this)", null);
        p.setDefinition(new CpsFlowDefinition(CpsFlowExecutionMemoryTest.class.getName() + ".register(this); node {load 'lib.groovy'; evaluate(readFile('lib.groovy'))}", false));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertFalse(((CpsFlowExecution) b.getExecution()).getProgramDataFile().exists());
        assertFalse(LOADERS.isEmpty());
        { // TODO it seems that the call to CpsFlowExecutionMemoryTest.register(Object) on a Script1 parameter creates a MetaMethodIndex.Entry.cachedStaticMethod.
          // In other words any call to a foundational API might leak classes. Why does Groovy need to do this?
          // Unclear whether this is a problem in a realistic environment; for the moment, suppressing it so the test can run with no SoftReference.
            MetaClass metaClass = ClassInfo.getClassInfo(CpsFlowExecutionMemoryTest.class).getMetaClass();
            Method clearInvocationCaches = metaClass.getClass().getDeclaredMethod("clearInvocationCaches");
            clearInvocationCaches.setAccessible(true);
            clearInvocationCaches.invoke(metaClass);
        }
        for (WeakReference<ClassLoader> loaderRef : LOADERS) {
            MemoryAssert.assertGC(loaderRef, false);
        }
    }

    @Ignore("creates classes such as script1493642504440203321963 in a new GroovyClassLoader.InnerLoader delegating to CleanGroovyClassLoader which are invisible to cleanUpHeap")
    @Test public void doNotUseConfigSlurper() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(CpsFlowExecutionMemoryTest.class.getName() + ".register(this); echo(/parsed ${new ConfigSlurper().parse('foo.bar.baz = 99')}/)", false));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertFalse(LOADERS.isEmpty());
        try { // as above
            Field f = ASTTransformationVisitor.class.getDeclaredField("compUnit");
            f.setAccessible(true);
            f.set(null, null);
        } catch (NoSuchFieldException e) {}
        { // TODO as above
            MetaClass metaClass = ClassInfo.getClassInfo(CpsFlowExecutionMemoryTest.class).getMetaClass();
            Method clearInvocationCaches = metaClass.getClass().getDeclaredMethod("clearInvocationCaches");
            clearInvocationCaches.setAccessible(true);
            clearInvocationCaches.invoke(metaClass);
        }
        for (WeakReference<ClassLoader> loaderRef : LOADERS) {
            MemoryAssert.assertGC(loaderRef, true);
        }
    }

}
