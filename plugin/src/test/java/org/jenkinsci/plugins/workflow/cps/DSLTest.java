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

package org.jenkinsci.plugins.workflow.cps;

import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import hudson.model.Result;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import static org.hamcrest.Matchers.containsString;

import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;

import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousStepExecution;
import org.jenkinsci.plugins.workflow.testMetaStep.AmbiguousEchoLowerStep;
import org.jenkinsci.plugins.workflow.testMetaStep.AmbiguousEchoUpperStep;

import static org.junit.Assert.*;

import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.Ignore;
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
    @ClassRule public static JenkinsRule r = new JenkinsRule();

    private WorkflowJob p;
    @Before public void newProject() throws Exception {
        p = r.createProject(WorkflowJob.class);
    }

    @Test public void overrideFunction() throws Exception {
        p.setDefinition(new CpsFlowDefinition("echo 'this came from a step'", true));
        r.assertLogContains("this came from a step", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
        p.setDefinition(new CpsFlowDefinition("def echo(s) {println s.toUpperCase()}\necho 'this came from my own function'\nsteps.echo 'but this is still from a step'", true));
        WorkflowRun b2 = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("THIS CAME FROM MY OWN FUNCTION", b2);
        r.assertLogContains("but this is still from a step", b2);
    }

    @Issue("JENKINS-43934")
    @Test public void flattenGString() throws Exception {
        p.setDefinition(new CpsFlowDefinition("def message = myJoin(['the', /${'message'.toLowerCase(Locale.ENGLISH)}/]); echo(/What is $message?/)", true));
        r.assertLogContains("What is the message?", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }
    public static class MyJoinStep extends Step {
        public final String args;
        @DataBoundConstructor public MyJoinStep(String args) {this.args = args;}
        @Override public StepExecution start(StepContext context) throws Exception {
            return new Exec(context, args);
        }
        private static class Exec extends SynchronousStepExecution<String> {
            final String args;
            Exec(StepContext context, String args) {
                super(context);
                this.args = args;
            }
            @Override protected String run() throws Exception {
                return args;
            }
        }
        @TestExtension public static class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {
                return "myJoin";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }
            @Override public Step newInstance(Map<String, Object> arguments) throws Exception {
                List<?> args = (List<?>) arguments.get("args");
                StringBuilder b = new StringBuilder();
                for (Object arg : args) {
                    if (b.length() > 0) {
                        b.append(' ');
                    }
                    b.append((String) arg);
                }
                return new MyJoinStep(b.toString());
            }
        }
    }

    @Issue("JENKINS-43934")
    @Test public void flattenGString2() throws Exception {
        p.setDefinition(new CpsFlowDefinition("echo pops(pojo(/running #$BUILD_NUMBER/))", true));
        r.assertLogContains("running #1", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }
    public static class Pojo extends AbstractDescribableImpl<Pojo> {
        public final String x;
        @DataBoundConstructor public Pojo(String x) {this.x = x;}
        @Symbol("pojo")
        @TestExtension public static class DescriptorImpl extends Descriptor<Pojo> {}
    }
    public static class Pops extends Step {
        public final Pojo pojo;
        @DataBoundConstructor public Pops(Pojo pojo) {this.pojo = pojo;}
        @Override public StepExecution start(StepContext context) throws Exception {
            return new Exec(context, pojo);
        }
        private static class Exec extends SynchronousStepExecution<String> {
            final Pojo pojo;
            Exec(StepContext context, Pojo pojo) {
                super(context);
                this.pojo = pojo;
            }

            @Override protected String run() throws Exception {
                return pojo.x;
            }
        }
        @TestExtension public static class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {
                return "pops";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }
        }
    }

    /**
     * Tests the ability to execute meta-step with clean syntax
     */
    @Issue("JENKINS-29922")
    @Test
    public void dollar_class_must_die() throws Exception {
        p.setDefinition(new CpsFlowDefinition("california ocean:'pacific', mountain:'sierra'", true));
        r.assertLogContains("California from pacific to sierra", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    /**
     * Split arguments between meta step and state
     */
    @Issue("JENKINS-29922")
    @Test
    public void dollar_class_must_die2() throws Exception {
        p.setDefinition(new CpsFlowDefinition("california ocean:'pacific', mountain:'sierra', moderate:true", true));
        assertThat(JenkinsRule.getLog(r.assertBuildStatusSuccess(p.scheduleBuild2(0))).replace("\r\n", "\n"), containsString("Introducing california\nCalifornia from pacific to sierra"));
    }

    /**
     * Split arguments between meta step and state
     */
    @Issue("JENKINS-29922")
    @Test
    public void dollar_class_must_die3() throws Exception {
        p.setDefinition(new CpsFlowDefinition("nevada()", true));
        r.assertLogContains("All For Our Country", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    /**
     * Split arguments between meta step and state, when argument is colliding
     */
    @Issue("JENKINS-29922")
    @Test
    public void dollar_class_must_die_colliding_argument() throws Exception {
        p.setDefinition(new CpsFlowDefinition("newYork motto:'Empire', moderate:true", true));
        WorkflowRun run = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        assertThat(JenkinsRule.getLog(run).replace("\r\n", "\n"), containsString("Introducing newYork\nThe Empire State"));
        r.assertLogNotContains("New York can be moderate in spring or fall", run);
    }

    /**
     * Single argument state
     */
    @Issue("JENKINS-29922")
    @Test
    public void dollar_class_must_die_onearg() throws Exception {
        p.setDefinition(new CpsFlowDefinition("newYork 'Empire'", true));
        r.assertLogContains("The Empire State", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }

    @Issue("JENKINS-29922")
    @Test
    public void nonexistentFunctions() throws Exception {
        p.setDefinition(new CpsFlowDefinition("nonexistent()", true));
        WorkflowRun b = r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("nonexistent", b);
        r.assertLogContains("wrapInCurve", b);
        r.assertLogContains("polygon", b);
    }

    @Issue("JENKINS-29922")
    @Test public void runMetaBlockStep() throws Exception {
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
        p.setDefinition(new CpsFlowDefinition("monomorphStep([firstArg:'one', secondArg:'two'])", true));
        r.assertLogContains("First arg: one, second arg: two", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
        WorkflowRun run = p.getLastBuild();
        LinearScanner scanner = new LinearScanner();
        FlowNode node = scanner.findFirstMatch(run.getExecution().getCurrentHeads(), new NodeStepTypePredicate("monomorphStep"));
        ArgumentsAction argumentsAction = node.getPersistentAction(ArgumentsAction.class);
        Assert.assertNotNull(argumentsAction);
        Assert.assertEquals("one,two", ArgumentsAction.getStepArgumentsAsString(node));
    }

    @Issue("JENKINS-29711")
    @Test
    public void monomorphicSymbol() throws Exception {
        p.setDefinition(new CpsFlowDefinition("monomorphWithSymbolStep monomorphSymbol(firstArg: 'one', secondArg: 'two')", true));
        r.assertLogContains("First arg: one, second arg: two", r.assertBuildStatusSuccess(p.scheduleBuild2(0)));
    }


    /**
     * Tests the ability to execute a step with an unnamed monomorphic list argument.
     */
    @Issue("JENKINS-29711")
    @Test
    public void monomorphicList() throws Exception {
        p.setDefinition(new CpsFlowDefinition("monomorphListStep([[firstArg:'one', secondArg:'two'], [firstArg:'three', secondArg:'four']])", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("First arg: one, second arg: two", b);
        r.assertLogContains("First arg: three, second arg: four", b);
    }

    @Issue("JENKINS-29711")
    @Test
    public void monomorphicListWithSymbol() throws Exception {
        p.setDefinition(new CpsFlowDefinition("monomorphListSymbolStep([monomorphSymbol(firstArg: 'one', secondArg: 'two'), monomorphSymbol(firstArg: 'three', secondArg: 'four')])", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("First arg: one, second arg: two", b);
        r.assertLogContains("First arg: three, second arg: four", b);
    }

    @Issue("JENKINS-38037")
    @Test
    public void metaStepSyntaxForDataBoundSetters() throws Exception {
        p.setDefinition(new CpsFlowDefinition("multiShape(count: 2, name: 'pentagon') { echo 'Multiple shapes' }", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("wrapping in a group of 2 instances of pentagon", b);
        r.assertLogContains("Multiple shapes", b);
    }

    @Issue("JENKINS-38169")
    @Test
    public void namedSoleParamForStep() throws Exception {
        p.setDefinition(new CpsFlowDefinition("echo message:'Hello world'", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Hello world", b);
    }

    @Issue("JENKINS-37538")
    @Test public void contextClassLoader() throws Exception {
        p.setDefinition(new CpsFlowDefinition("try {def c = classLoad(getClass().name); error(/did not expect to be able to load ${c} from ${c.classLoader}/)} catch (ClassNotFoundException x) {echo(/good, got ${x}/)}", false));
        r.assertBuildStatusSuccess(p.scheduleBuild2(0));
    }

    /**
    * Tests the ability to execute a user defined closure
    */
    @Test public void userDefinedClosureInvocationExecution() throws Exception {
        p.setDefinition(new CpsFlowDefinition("binding[\"my_closure\"] = { \n" +
                                              " sleep 1 \n" + 
                                              " echo \"my closure!\" \n" + 
                                              "}\n" + 
                                              "my_closure() ", false));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("my closure!", b); 
    }
 
    /**
    * Tests the ability to execute a user defined closure with no arguments
    */
    @Test public void userDefinedClosure0ArgsExecution() throws Exception {
         p.setDefinition(new CpsFlowDefinition("binding.setVariable(\"my_closure\", { echo \"my closure!\" })\n my_closure() ", false));
         WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
         r.assertLogContains("my closure!", b); 
    }

    /**
    * Tests the ability to execute a user defined closure with one arguments
    */
    @Test public void userDefinedClosure1ArgInvocationExecution() throws Exception {
        p.setDefinition(new CpsFlowDefinition("my_closure = { String message -> \n" +
                                              "  echo message \n" +
                                              "}\n" + 
                                              "my_closure(\"my message!\") ", false));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("my message!", b); 
    }

    /**
    * Tests the ability to execute a user defined closure with 2 arguments
    */
    @Test public void userDefinedClosure2ArgInvocationExecution() throws Exception {
        p.setDefinition(new CpsFlowDefinition("my_closure = { String message1, String message2 -> \n" +
                                              "  echo \"my message is ${message1} and ${message2}\" \n" +
                                              "}\n" + 
                                              "my_closure(\"string1\", \"string2\") ", false));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("my message is string1 and string2", b); 
    }

    /**
    * Tests untyped arguments 
    */
    @Test public void userDefinedClosureUntypedArgInvocationExecution() throws Exception {
        p.setDefinition(new CpsFlowDefinition("my_closure = { a , b -> \n" +
                                                      "  echo \"my message is ${a} and ${b}\" \n" +
                                                      "}\n" +
                                                      "my_closure(\"string1\" ,2)",false));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("my message is string1 and 2", b);
    }
    
    /**
    * Tests the ability to execute a user defined closure with variable arguments
    * note: currently fails because CpsClosure's don't currently work with varargs
    *
    */
	@Ignore
    @Test public void userDefinedClosureVarArgInvocationExecution() throws Exception {
        p.setDefinition(new CpsFlowDefinition("my_closure = { String message, Integer... n -> \n" +
                                              "  println message \n" + 
                                              "  println n.sum() \n" +
                                              "}\n" +
                                              "my_closure(\"testing\",1,2,3) ", false));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("testing", b);
        r.assertLogContains("6", b); 
    }
    
    @Test public void quotedStep() throws Exception {
        p.setDefinition(new CpsFlowDefinition("'echo' 'Hello1'\n" +
                                              "\"echo\" 'Hello2'", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Hello1", b);
        r.assertLogContains("Hello2", b);
    }

    @Test public void fullyQualifiedStep() throws Exception {
        p.setDefinition(new CpsFlowDefinition("'org.jenkinsci.plugins.workflow.steps.EchoStep' 'Hello, world!'", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("Hello, world!", b);
    }

    @Test public void fullyQualifiedAmbiguousStep() throws Exception {
        p.setDefinition(new CpsFlowDefinition(
                "'org.jenkinsci.plugins.workflow.testMetaStep.AmbiguousEchoLowerStep' 'HeLlO'\n" +
                "'org.jenkinsci.plugins.workflow.testMetaStep.AmbiguousEchoUpperStep' 'GoOdByE'", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("hello", b);
        r.assertLogContains("GOODBYE", b);
        r.assertLogNotContains("Warning: Invoking ambiguous Pipeline Step", b);
    }

    @Test public void ambiguousStepsRespectOrdinal() throws Exception {
        p.setDefinition(new CpsFlowDefinition("ambiguousEcho 'HeLlO'\n", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        r.assertLogContains("HELLO", b);
        r.assertLogContains("Warning: Invoking ambiguous Pipeline Step", b);
        r.assertLogContains("any of the following steps: [" + AmbiguousEchoUpperStep.class.getName() + ", " + AmbiguousEchoLowerStep.class.getName() + "]", b);
    }

    @Test public void  strayParameters() throws Exception {
        p.setDefinition(new CpsFlowDefinition("node {sleep time: 1, units: 'SECONDS', comment: 'units is a typo'}", true));
        WorkflowRun b =  r.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0));
        r.assertLogContains("IllegalArgumentException: WARNING: Unknown parameter(s) found for class type " +
                "'org.jenkinsci.plugins.workflow.steps.SleepStep': comment,units", b);
    }

    public static class CLStep extends Step {
        public final String name;
        @DataBoundConstructor public CLStep(String name) {this.name = name;}
        @Override public StepExecution start(StepContext context) throws Exception {
            return new Execution(name, context);
        }
        static class Execution extends SynchronousStepExecution<Class<?>> {
            private final String name;
            Execution(String name, StepContext context) {
                super(context);
                this.name = name;
            }
            @Override protected Class<?> run() throws Exception {
                return Thread.currentThread().getContextClassLoader().loadClass(name);
            }
        }
        @TestExtension public static class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {
                return "classLoad";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }
        }
    }

}
