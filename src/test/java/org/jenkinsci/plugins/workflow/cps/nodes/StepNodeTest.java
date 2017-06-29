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

package org.jenkinsci.plugins.workflow.cps.nodes;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import hudson.model.Result;
import hudson.tasks.junit.JUnitResultArchiver;
import java.util.List;
import java.util.logging.Level;
import org.apache.commons.lang.StringUtils;
import static org.hamcrest.Matchers.*;
import org.jenkinsci.plugins.configfiles.buildwrapper.ConfigFileBuildWrapper;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import static org.junit.Assert.*;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

public class StepNodeTest {

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();
    @Rule public LoggerRule logging = new LoggerRule().record(StepAtomNode.class, Level.FINE);
    
    @Issue("JENKINS-45109")
    @Test public void metastepConsole() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "  configFileProvider([]) {\n" +
            "    writeFile text: '''<testsuite name='a'><testcase name='c'><error>failed</error></testcase></testsuite>''', file: 'x.xml'\n" +
            "    junit 'x.xml'\n" +
            "  }\n" +
            "}", true));
        WorkflowRun b = r.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0));
        List<FlowNode> coreStepNodes = new DepthFirstScanner().filteredNodes(b.getExecution(), new NodeStepTypePredicate("step"));
        assertThat(coreStepNodes, hasSize(1));
        assertEquals("junit", coreStepNodes.get(0).getDisplayFunctionName());
        assertEquals(r.jenkins.getDescriptor(JUnitResultArchiver.class).getDisplayName(), coreStepNodes.get(0).getDisplayName());
        List<FlowNode> coreWrapperStepNodes = new DepthFirstScanner().filteredNodes(b.getExecution(), Predicates.and(new NodeStepTypePredicate("wrap"), new Predicate<FlowNode>() {
            @Override public boolean apply(FlowNode n) {
                return n instanceof StepStartNode && !((StepStartNode) n).isBody();
            }
        }));
        assertThat(coreWrapperStepNodes, hasSize(1));
        assertEquals("configFileProvider", coreWrapperStepNodes.get(0).getDisplayFunctionName());
        assertEquals(r.jenkins.getDescriptor(ConfigFileBuildWrapper.class).getDisplayName() + " : Start", coreWrapperStepNodes.get(0).getDisplayName());
        r.assertLogContains("[Pipeline] junit", b);
        r.assertLogContains("[Pipeline] configFileProvider", b);
        r.assertLogContains("[Pipeline] // configFileProvider", b);
    }

    @Test public void metastepConsoleShellClass() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "  wrap([$class: 'ConfigFileBuildWrapper', managedFiles: []]) {\n" +
            "    writeFile text: '''<testsuite name='a'><testcase name='c'><error>failed</error></testcase></testsuite>''', file: 'x.xml'\n" +
            "    step([$class: 'JUnitResultArchiver', testResults: 'x.xml'])\n" +
            "  }\n" +
            "}", true));
        WorkflowRun b = r.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0));
        List<FlowNode> coreStepNodes = new DepthFirstScanner().filteredNodes(b.getExecution(), new NodeStepTypePredicate("step"));
        assertThat(coreStepNodes, hasSize(1));
        assertEquals("junit", coreStepNodes.get(0).getDisplayFunctionName());
        assertEquals(r.jenkins.getDescriptor(JUnitResultArchiver.class).getDisplayName(), coreStepNodes.get(0).getDisplayName());
        List<FlowNode> coreWrapperStepNodes = new DepthFirstScanner().filteredNodes(b.getExecution(), Predicates.and(new NodeStepTypePredicate("wrap"), new Predicate<FlowNode>() {
            @Override public boolean apply(FlowNode n) {
                return n instanceof StepStartNode && !((StepStartNode) n).isBody();
            }
        }));
        assertThat(coreWrapperStepNodes, hasSize(1));
        assertEquals("configFileProvider", coreWrapperStepNodes.get(0).getDisplayFunctionName());
        assertEquals(r.jenkins.getDescriptor(ConfigFileBuildWrapper.class).getDisplayName() + " : Start", coreWrapperStepNodes.get(0).getDisplayName());
        r.assertLogContains("[Pipeline] junit", b);
        r.assertLogContains("[Pipeline] configFileProvider", b);
        r.assertLogContains("[Pipeline] // configFileProvider", b);
    }

    @Test public void metastepConsoleRaw() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "  wrap(new org.jenkinsci.plugins.configfiles.buildwrapper.ConfigFileBuildWrapper([])) {\n" +
            "    writeFile text: '''<testsuite name='a'><testcase name='c'><error>failed</error></testcase></testsuite>''', file: 'x.xml'\n" +
            "    step(new hudson.tasks.junit.JUnitResultArchiver('x.xml'))\n" +
            "  }\n" +
            "}", false));
        WorkflowRun b = r.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0));
        List<FlowNode> coreStepNodes = new DepthFirstScanner().filteredNodes(b.getExecution(), new NodeStepTypePredicate("step"));
        assertThat(coreStepNodes, hasSize(1));
        assertEquals("junit", coreStepNodes.get(0).getDisplayFunctionName());
        assertEquals(r.jenkins.getDescriptor(JUnitResultArchiver.class).getDisplayName(), coreStepNodes.get(0).getDisplayName());
        List<FlowNode> coreWrapperStepNodes = new DepthFirstScanner().filteredNodes(b.getExecution(), Predicates.and(new NodeStepTypePredicate("wrap"), new Predicate<FlowNode>() {
            @Override public boolean apply(FlowNode n) {
                return n instanceof StepStartNode && !((StepStartNode) n).isBody();
            }
        }));
        assertThat(coreWrapperStepNodes, hasSize(1));
        assertEquals("configFileProvider", coreWrapperStepNodes.get(0).getDisplayFunctionName());
        assertEquals(r.jenkins.getDescriptor(ConfigFileBuildWrapper.class).getDisplayName() + " : Start", coreWrapperStepNodes.get(0).getDisplayName());
        r.assertLogContains("[Pipeline] junit", b);
        r.assertLogContains("[Pipeline] configFileProvider", b);
        r.assertLogContains("[Pipeline] // configFileProvider", b);
    }

    @Ignore("TODO ArgumentsAction.getResolvedArguments does not yet handle NotStoredReason sensibly")
    @Test public void metastepConsoleNotStoredArgument() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        String spaces = StringUtils.repeat(" ", 1025); // cf. ArgumentsAction.MAX_RETAINED_LENGTH
        p.setDefinition(new CpsFlowDefinition(
            "node {\n" +
            "  configFileProvider([]) {\n" +
            "    writeFile text: '''<testsuite name='a'><testcase name='c'><error>failed</error></testcase></testsuite>''', file: 'x.xml'\n" +
            "    junit 'x.xml," + spaces + "'\n" +
            "  }\n" +
            "}", true));
        WorkflowRun b = r.assertBuildStatus(Result.UNSTABLE, p.scheduleBuild2(0));
        List<FlowNode> coreStepNodes = new DepthFirstScanner().filteredNodes(b.getExecution(), new NodeStepTypePredicate("step"));
        assertThat(coreStepNodes, hasSize(1));
        assertEquals("junit", coreStepNodes.get(0).getDisplayFunctionName());
        assertEquals(r.jenkins.getDescriptor(JUnitResultArchiver.class).getDisplayName(), coreStepNodes.get(0).getDisplayName());
        List<FlowNode> coreWrapperStepNodes = new DepthFirstScanner().filteredNodes(b.getExecution(), Predicates.and(new NodeStepTypePredicate("wrap"), new Predicate<FlowNode>() {
            @Override public boolean apply(FlowNode n) {
                return n instanceof StepStartNode && !((StepStartNode) n).isBody();
            }
        }));
        assertThat(coreWrapperStepNodes, hasSize(1));
        assertEquals("configFileProvider", coreWrapperStepNodes.get(0).getDisplayFunctionName());
        assertEquals(r.jenkins.getDescriptor(ConfigFileBuildWrapper.class).getDisplayName() + " : Start", coreWrapperStepNodes.get(0).getDisplayName());
        r.assertLogContains("[Pipeline] junit", b);
        r.assertLogContains("[Pipeline] configFileProvider", b);
        r.assertLogContains("[Pipeline] // configFileProvider", b);
    }

}
