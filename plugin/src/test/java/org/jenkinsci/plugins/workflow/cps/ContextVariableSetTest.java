/*
 * The MIT License
 *
 * Copyright 2019 CloudBees, Inc.
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

import hudson.console.LineTransformationOutputStream;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Run;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.log.TaskListenerDecorator;
import org.jenkinsci.plugins.workflow.steps.DynamicContext;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.StepExecutions;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

import org.junit.Rule;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

@Issue("JENKINS-41854")
public class ContextVariableSetTest {

    private static final Logger LOGGER = Logger.getLogger(ContextVariableSetTest.class.getName());

    @ClassRule public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule public JenkinsRule r = new JenkinsRule();

    @Rule public LoggerRule logging = new LoggerRule().record(DynamicContext.class, Level.FINE).record(CpsThread.class, Level.FINE).record(ContextVariableSet.class, Level.FINE);

    @Test public void smokes() throws Exception {
        r.jenkins.setNumExecutors(1);
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "decorate(message: 'outer') {\n" +
            "  echo 'one'\n" +
            "  decorate {\n" +
            "    echo 'two'\n" +
            "    node {\n" +
            "      echo 'three'\n" +
            "      decorate(message: 'inner') {\n" +
            "        echo 'four'\n" +
            "      }\n" +
            "    }\n" +
            "  }\n" +
            "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("[outer] one", b);
        r.assertLogNotContains("] [outer] one", b);
        r.assertLogContains("[outer] two", b);
        r.assertLogNotContains("] [outer] two", b);
        r.assertLogContains("[p #1 1/1] [outer] three", b);
        r.assertLogNotContains("] [p #1 1/1] [outer] three", b);
        r.assertLogContains("[inner] [p #1 1/1] [outer] four", b);
        r.assertLogNotContains("] [inner] [p #1 1/1] [outer] four", b);
    }
    private static final class DecoratorImpl extends TaskListenerDecorator {
        private final String message;
        DecoratorImpl(String message) {
            this.message = message;
        }
        @Override public OutputStream decorate(OutputStream logger) throws IOException, InterruptedException {
            return new LineTransformationOutputStream() {
                final String prefix = "[" + message + "] ";
                @Override protected void eol(byte[] b, int len) throws IOException {
                    logger.write(prefix.getBytes());
                    logger.write(b, 0, len);
                }
                @Override public void close() throws IOException {
                    super.close();
                    logger.close();
                }
                @Override public void flush() throws IOException {
                    logger.flush();
                }
            };
        }
        @Override public String toString() {
            return "DecoratorImpl[" + message + "]";
        }
    }
    @TestExtension("smokes") public static final class DecoratorContext extends DynamicContext.Typed<TaskListenerDecorator> {
        @Override protected Class<TaskListenerDecorator> type() {
            return TaskListenerDecorator.class;
        }
        @Override protected TaskListenerDecorator get(DelegatedContext context) throws IOException, InterruptedException {
            if (context.get(YesPleaseDecorate.class) == null) {
                return null;
            }
            // Exercising lookup of something which is special-cased in DefaultStepContext.get.
            Run<?, ?> build = context.get(Run.class);
            assertNotNull(build);
            // PlaceholderExecutable defines Computer and again DefaultStepContext translates that to Node.
            Node node = context.get(Node.class);
            if (node == null) {
                return null;
            }
            // Literally in the context.
            Executor exec = context.get(Executor.class);
            if (exec == null) {
                return null;
            }
            String message = build + " " + (exec.getNumber() + 1) + "/" + node.getNumExecutors();
            // Recursive lookup of object of same type from an enclosing scope.
            TaskListenerDecorator original = context.get(TaskListenerDecorator.class);
            DecoratorImpl subsequent = new DecoratorImpl(message);
            LOGGER.log(Level.INFO, "merging {0} with {1}", new Object[] {original, subsequent});
            return TaskListenerDecorator.merge(original, subsequent);
        }
        @Override public String toString() {
            return "DecoratorContext";
        }
    }
    private static final class YesPleaseDecorate implements Serializable {}
    public static final class DecoratorStep extends Step {
        @DataBoundConstructor public DecoratorStep() {}
        @DataBoundSetter public @CheckForNull String message;
        @Override public StepExecution start(StepContext context) throws Exception {
            return StepExecutions.block(context, (c, invoker) -> {
                if (message != null) {
                    TaskListenerDecorator original = c.get(TaskListenerDecorator.class);
                    DecoratorImpl subsequent = new DecoratorImpl(message);
                    LOGGER.log(Level.INFO, "merging {0} with {1}", new Object[] {original, subsequent});
                    invoker.withContext(TaskListenerDecorator.merge(original, subsequent));
                } else {
                    invoker.withContext(new YesPleaseDecorate());
                }
            });
        }
        @TestExtension("smokes") public static final class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {
                return "decorate";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }
            @Override public boolean takesImplicitBlockArgument() {
                return true;
            }
        }
    }

    @Test public void dynamicVsStatic() throws Exception {
        WorkflowJob p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition(
            "withStaticMessage('one') {\n" +
            "  echo(/one: ${getMessage()}/)\n" +
            "  withDynamicMessage('two') {\n" +
            "    echo(/two: ${getMessage()}/)\n" +
            "    withStaticMessage('three') {\n" +
            "      echo(/three: ${getMessage()}/)\n" +
            "    }\n" +
            "  }\n" +
            "}", true));
        WorkflowRun b = r.buildAndAssertSuccess(p);
        r.assertLogContains("one: one", b);
        r.assertLogContains("two: two", b);
        r.assertLogContains("three: three", b);
    }
    private static final class Message implements Serializable {
        final String text;
        Message(String text) {
            this.text = text;
        }
        @Override public String toString() {
            return "Message:" + text;
        }
    }
    public static final class GetMessageStep extends Step {
        @DataBoundConstructor public GetMessageStep() {}
        @Override public StepExecution start(StepContext context) throws Exception {
            return StepExecutions.synchronous(context, c -> {
                Message message = c.get(Message.class);
                return message != null ? message.text : null;
            });
        }
        @TestExtension("dynamicVsStatic") public static final class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {
                return "getMessage";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }
        }
    }
    public static final class WithStaticMessageStep extends Step {
        public final String text;
        @DataBoundConstructor public WithStaticMessageStep(String text) {
            this.text = text;
        }
        @Override public StepExecution start(StepContext context) throws Exception {
            return StepExecutions.block(context, (_context, invoker) -> invoker.withContext(new Message(text)));
        }
        @TestExtension("dynamicVsStatic") public static final class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {
                return "withStaticMessage";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }
            @Override public boolean takesImplicitBlockArgument() {
                return true;
            }
        }
    }
    private static final class DynamicMessage implements Serializable {
        final String text;
        DynamicMessage(String text) {
            this.text = text;
        }
        @Override public String toString() {
            return "DynamicMessage:" + text;
        }
    }
    @TestExtension("dynamicVsStatic") public static final class DC extends DynamicContext.Typed<Message> {
        @Override protected Class<Message> type() {
            return Message.class;
        }
        @Override protected Message get(DelegatedContext context) throws IOException, InterruptedException {
            DynamicMessage dynamicMessage = context.get(DynamicMessage.class);
            return dynamicMessage != null ? new Message(dynamicMessage.text) : null;
        }
    }
    public static final class WithDynamicMessageStep extends Step {
        public final String text;
        @DataBoundConstructor public WithDynamicMessageStep(String text) {
            this.text = text;
        }
        @Override public StepExecution start(StepContext context) throws Exception {
            return StepExecutions.block(context, (_context, invoker) -> invoker.withContext(new DynamicMessage(text)));
        }
        @TestExtension("dynamicVsStatic") public static final class DescriptorImpl extends StepDescriptor {
            @Override public String getFunctionName() {
                return "withDynamicMessage";
            }
            @Override public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }
            @Override public boolean takesImplicitBlockArgument() {
                return true;
            }
        }
    }

}
