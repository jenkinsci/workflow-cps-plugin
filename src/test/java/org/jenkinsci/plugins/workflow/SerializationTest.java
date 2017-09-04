package org.jenkinsci.plugins.workflow;

import groovy.lang.Closure;
import hudson.model.Result;
import hudson.slaves.DumbSlave;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepExecutionImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Test;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import static org.junit.Assert.assertTrue;
import org.junit.Ignore;
import org.jvnet.hudson.test.Issue;

/**
 * Tests related to serialization of program state.
 */
public class SerializationTest extends SingleJobTestBase {

    /**
     * When wokflow execution runs into a serialization problem, can we handle that situation gracefully?
     */
    @Test
    public void stepExecutionFailsToPersist() throws Exception {
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(join(
                        "node {",
                        "  persistenceProblem()",
                        "}"
                )));

                startBuilding();
                waitForWorkflowToSuspend();

                // TODO: let the ripple effect of a failure run to the completion.
                while (b.isBuilding())
                    try {
                        waitForWorkflowToSuspend();
                    } catch (Exception x) {
                        // ignore persistence failure
                        String message = x.getMessage();
                        if (message == null || !message.contains("Failed to persist")) {
                            throw x;
                        }
                    }

                story.j.assertBuildStatus(Result.FAILURE, b);
                story.j.assertLogContains("java.lang.RuntimeException: testing the forced persistence failure behaviour", b);
            }
        });
        story.addStep(new Statement() {
            @Override
            public void evaluate() throws Throwable {
                rebuildContext(story.j);

                story.j.assertBuildStatus(Result.FAILURE, b);
            }
        });
    }
    /**
     * {@link Step} that fails to persist. Used to test the behaviour of error reporting/recovery.
     */
    public static class PersistenceProblemStep extends AbstractStepImpl {
        @DataBoundConstructor
        public PersistenceProblemStep() {
            super();
        }
        @TestExtension("stepExecutionFailsToPersist")
        public static final class DescriptorImpl extends AbstractStepDescriptorImpl {
            public DescriptorImpl() {
                super(PersistenceProblemStepExecution.class);
            }
            @Override
            public String getFunctionName() {
                return "persistenceProblem";
            }
            @Override
            public String getDisplayName() {
                return "Problematic Persistence";
            }
        }
        /**
         * {@link StepExecution} that fails to serialize.
         *
         * Used to test the error recovery path of {@link WorkflowJob}.
         */
        public static class PersistenceProblemStepExecution extends AbstractStepExecutionImpl {
            public final Object notSerializable = new Object();
            private Object writeReplace() {
                throw new RuntimeException("testing the forced persistence failure behaviour");
            }
            @Override
            public boolean start() throws Exception {
                return false;
            }
        }
    }

    /**
     * Workflow captures a stateful object, and we verify that it survives the restart
     */
    @Test public void persistEphemeralObject() throws Exception {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                jenkins().setNumExecutors(0);
                DumbSlave s = createSlave(story.j);
                String nodeName = s.getNodeName();

                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "def s = jenkins.model.Jenkins.instance.getComputer('" + nodeName + "')\n" +
                    "def r = s.node.rootPath\n" +
                    "def p = r.getRemote()\n" +

                    "semaphore 'wait'\n" +

                    // make sure these values are still alive
                    "assert s.nodeName=='" + nodeName + "'\n" +
                    "assert r.getRemote()==p : r.getRemote() + ' vs ' + p;\n" +
                    "assert r.channel==s.channel : r.channel.toString() + ' vs ' + s.channel\n"));

                startBuilding();
                SemaphoreStep.waitForStart("wait/1", b);
                assertTrue(b.isBuilding());
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                assertThatWorkflowIsSuspended();
                SemaphoreStep.success("wait/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
            }
        });
    }

    @Test public void nonCps() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "echo \"first parse: ${parse('foo <version>1.0</version> bar')}\"\n" +
                    "echo \"second parse: ${parse('foo bar')}\"\n" +
                    "@NonCPS def parse(text) {\n" +
                    "  def matcher = text =~ '<version>(.+)</version>'\n" +
                    "  matcher ? matcher[0][1] : null\n" +
                    "}\n", true));
                b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("first parse: 1.0", b);
                story.j.assertLogContains("second parse: null", b);
            }
        });
    }

    @Issue("JENKINS-26481")
    @Test public void eachClosure() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "node {\n" +
                    "  ['a', 'b', 'c'].each {f -> writeFile file: f, text: f}\n" +
                    "  def text = ''\n" +
                    "  ['a', 'b', 'c'].each {f -> semaphore f; text += readFile f}\n" +
                    "  echo text\n" +
                    "}\n", true));
                b = p.scheduleBuild2(0).waitForStart();
                SemaphoreStep.waitForStart("a/1", b);
                SemaphoreStep.success("a/1", null);
                SemaphoreStep.waitForStart("b/1", b);
            }
        });
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                rebuildContext(story.j);
                SemaphoreStep.success("b/1", null);
                SemaphoreStep.waitForStart("c/1", b);
                SemaphoreStep.success("c/1", null);
                story.j.assertBuildStatusSuccess(story.j.waitForCompletion(b));
                story.j.assertLogContains("abc", b);
            }
        });
    }

    /**
     * Verifies that we can use closures in ways that were not affected by JENKINS-26481.
     * In particular:
     * <ul>
     * <li>on non-CPS-transformed {@link Closure}s
     * <li>on closures passed to methods defined in Pipeline script
     * <li>on closures passed to methods which did not declare {@link Closure} as a parameter type and so presumably are not going to try to call them
     * </ul>
     */
    @Issue("JENKINS-26481")
    @Test public void eachClosureNonCps() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "@NonCPS def fine() {\n" +
                    "  def text = ''\n" +
                    "  ['a', 'b', 'c'].each {it -> text += it}\n" +
                    "  text\n" +
                    "}\n" +
                    "def takesMyOwnClosure(body) {\n" +
                    "  node {\n" +
                    "    def list = []\n" +
                    "    list += body\n" +
                    "    echo list[0]()\n" +
                    "  }\n" +
                    "}\n" +
                    "takesMyOwnClosure {\n" +
                    "  fine()\n" +
                    "}\n", true));
                b = story.j.assertBuildStatusSuccess(p.scheduleBuild2(0));
                story.j.assertLogContains("abc", b);
            }
        });
    }

    @Ignore("TODO JENKINS-31314: calls writeFile just once, echoes null (i.e., return value of writeFile), then succeeds")
    @Test public void nonCpsContinuable() {
        story.addStep(new Statement() {
            @Override public void evaluate() throws Throwable {
                p = jenkins().createProject(WorkflowJob.class, "demo");
                p.setDefinition(new CpsFlowDefinition(
                    "@NonCPS def shouldBomb() {\n" +
                    "  def text = ''\n" +
                    "  ['a', 'b', 'c'].each {it -> writeFile file: it, text: it; text += it}\n" +
                    "  text\n" +
                    "}\n" +
                    "node {\n" +
                    "  echo shouldBomb()\n" +
                    "}\n", true));
                b = story.j.assertBuildStatus(Result.FAILURE, p.scheduleBuild2(0).get());
            }
        });
    }

}
