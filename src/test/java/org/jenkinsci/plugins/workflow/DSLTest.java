/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

package org.jenkinsci.plugins.workflow;

import hudson.model.Result;
import javax.inject.Inject;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousStepExecution;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Verifies general DSL functionality.
 */
public class DSLTest {
    
    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();
    @Rule public JenkinsRule r = new JenkinsRule();

    @Test public void overrideFunction() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo 'this came from a step'"));
        r.assertLogContains("this came from a step", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
        ScriptApproval.get().approveSignature("method java.lang.String toUpperCase"); // TODO until script-security whitelists this kind of thing by default
        p.setDefinition(new CpsFlowDefinition("def echo(s) {println s.toUpperCase()}\necho 'this came from my own function'\nsteps.echo 'but this is still from a step'", true));
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("THIS CAME FROM MY OWN FUNCTION", b2);
        r.assertLogContains("but this is still from a step", b2);
    }

    @Test public void flattenGString() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("def x = 'the message'; echo \"What is ${x}?\""));
        r.assertLogContains("What is the message?", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    /**
     * Tests the ability to execute meta-step with clean syntax
     */
    @Issue("JENKINS-29922")
    @Test
    public void dollar_class_must_die() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "die1");
        p.setDefinition(new CpsFlowDefinition("california ocean:'pacific', mountain:'sierra'"));
        r.assertLogContains("California from pacific to sierra", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    /**
     * Split arguments between meta step and state
     */
    @Issue("JENKINS-29922")
    @Test
    public void dollar_class_must_die2() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "die2");
        p.setDefinition(new CpsFlowDefinition("california ocean:'pacific', mountain:'sierra', moderate:true"));
        r.assertLogContains("Introducing california\nCalifornia from pacific to sierra", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    /**
     * Split arguments between meta step and state
     */
    @Issue("JENKINS-29922")
    @Test
    public void dollar_class_must_die3() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "die3");
        p.setDefinition(new CpsFlowDefinition("nevada()"));
        r.assertLogContains("All For Our Country", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    /**
     * Split arguments between meta step and state, when argument is colliding
     */
    @Issue("JENKINS-29922")
    @Test
    public void dollar_class_must_die_colliding_argument() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "die5");
        p.setDefinition(new CpsFlowDefinition("newYork motto:'Empire', moderate:true"));
        WorkflowRun run = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Introducing newYork\nThe Empire State", run);
        r.assertLogNotContains("New York can be moderate in spring or fall", run);
    }

    /**
     * Single argument state
     */
    @Issue("JENKINS-29922")
    @Test
    public void dollar_class_must_die_onearg() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "die4");
        p.setDefinition(new CpsFlowDefinition("newYork 'Empire'"));
        r.assertLogContains("The Empire State", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    @Issue("JENKINS-29922")
    @Test
    public void nonexistentFunctions() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("nonexistent()"));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("nonexistent", b);
        r.assertLogContains("wrapInCurve", b);
        r.assertLogContains("polygon", b);
    }

    @Issue("JENKINS-29922")
    @Test public void runMetaBlockStep() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("circle {echo 'interior is a disk'}", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("wrapping in a circle", b);
        r.assertLogContains("interior is a disk", b);
        p.setDefinition(new CpsFlowDefinition("polygon(17) {echo 'constructible with compass and straightedge'}", true));
        b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("wrapping in a 17-gon", b);
        r.assertLogContains("constructible with compass and straightedge", b);
    }

    /**
     * Tests the ability to execute a step with an unnamed monomorphic describable argument.
     */
    @Issue("JENKINS-29711")
    @Test
    public void monomorphic() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "mon");
        p.setDefinition(new CpsFlowDefinition("monomorphStep([firstArg:'one', secondArg:'two'])", true));
        r.assertLogContains("First arg: one, second arg: two", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    @Issue("JENKINS-29711")
    @Test
    public void monomorphicSymbol() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "monSymbol");
        p.setDefinition(new CpsFlowDefinition("monomorphWithSymbolStep([firstArg:'one', secondArg:'two'])", true));
        r.assertLogContains("First arg: one, second arg: two", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }


    /**
     * Tests the ability to execute a step with an unnamed monomorphic list argument.
     */
    @Issue("JENKINS-29711")
    @Test
    public void monomorphicList() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "monList");
        p.setDefinition(new CpsFlowDefinition("monomorphListStep([[firstArg:'one', secondArg:'two'], [firstArg:'three', secondArg:'four']])", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("First arg: one, second arg: two", b);
        r.assertLogContains("First arg: three, second arg: four", b);
    }

    @Issue("JENKINS-29711")
    @Test
    public void monomorphicListWithSymbol() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "monListSymbol");
        p.setDefinition(new CpsFlowDefinition("monomorphListSymbolStep([[firstArg:'one', secondArg:'two'], [firstArg:'three', secondArg:'four']])", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("First arg: one, second arg: two", b);
        r.assertLogContains("First arg: three, second arg: four", b);
    }


    @Test public void contextClassLoader() throws Exception {
        WorkflowJob p = r.jenkins.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("try {def c = classLoad(getClass().name); error(/did not expect to be able to load ${c} from ${c.classLoader}/)} catch (ClassNotFoundException x) {echo(/good, got ${x}/)}", false));
        r.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }
    public static class CLStep extends AbstractStepImpl {
        public final String name;
        @DataBoundConstructor public CLStep(String name) {this.name = name;}
        public static class Execution extends AbstractSynchronousStepExecution<Class<?>> {
            @Inject CLStep step;
            protected Class<?> run() throws Exception {
                return Thread.currentThread().getContextClassLoader().loadClass(step.name);
            }
        }
        @TestExtension("contextClassLoader") public static class DescriptorImpl extends AbstractStepDescriptorImpl {
            public DescriptorImpl() {super(Execution.class);}
            @Override public String getFunctionName() {
                return "classLoad";
            }
        }
    }

}
