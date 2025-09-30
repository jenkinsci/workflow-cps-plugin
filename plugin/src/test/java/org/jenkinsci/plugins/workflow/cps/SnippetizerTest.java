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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import groovy.lang.GroovyShell;
import hudson.model.Result;
import hudson.tasks.ArtifactArchiver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.steps.CoreStep;
import org.jenkinsci.plugins.workflow.steps.EchoStep;
import org.jenkinsci.plugins.workflow.steps.PwdStep;
import org.jenkinsci.plugins.workflow.steps.ReadFileStep;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.TimeoutStep;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.jenkinsci.plugins.workflow.support.steps.WorkspaceStep;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStep;
import org.jenkinsci.plugins.workflow.testMetaStep.Circle;
import org.jenkinsci.plugins.workflow.testMetaStep.Colorado;
import org.jenkinsci.plugins.workflow.testMetaStep.CurveMetaStep;
import org.jenkinsci.plugins.workflow.testMetaStep.EchoResultStep;
import org.jenkinsci.plugins.workflow.testMetaStep.EchoStringAndDoubleStep;
import org.jenkinsci.plugins.workflow.testMetaStep.Hawaii;
import org.jenkinsci.plugins.workflow.testMetaStep.Island;
import org.jenkinsci.plugins.workflow.testMetaStep.MonomorphicData;
import org.jenkinsci.plugins.workflow.testMetaStep.MonomorphicDataWithSymbol;
import org.jenkinsci.plugins.workflow.testMetaStep.MonomorphicListStep;
import org.jenkinsci.plugins.workflow.testMetaStep.MonomorphicListWithSymbolStep;
import org.jenkinsci.plugins.workflow.testMetaStep.MonomorphicStep;
import org.jenkinsci.plugins.workflow.testMetaStep.MonomorphicWithSymbolStep;
import org.jenkinsci.plugins.workflow.testMetaStep.Oregon;
import org.jenkinsci.plugins.workflow.testMetaStep.Polygon;
import org.jenkinsci.plugins.workflow.testMetaStep.StateMetaStep;
import org.jenkinsci.plugins.workflow.testMetaStep.chemical.CarbonMonoxide;
import org.jenkinsci.plugins.workflow.testMetaStep.chemical.DetectionMetaStep;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;
import org.jvnet.hudson.test.TestExtension;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.NoStaplerConstructorException;

// TODO these tests would better be moved to the respective plugins

public class SnippetizerTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @ClassRule
    public static LoggerRule logging = new LoggerRule().record(DescribableModel.class, Level.ALL);

    private static SnippetizerTester st = new SnippetizerTester(r);

    @Test
    public void basics() throws Exception {
        st.assertRoundTrip(new EchoStep("hello world"), "echo 'hello world'");
        ReadFileStep s = new ReadFileStep("build.properties");
        st.assertRoundTrip(s, "readFile 'build.properties'");
        s.setEncoding("ISO-8859-1");
        st.assertRoundTrip(s, "readFile encoding: 'ISO-8859-1', file: 'build.properties'");
    }

    @Email("https://groups.google.com/forum/#!topicsearchin/jenkinsci-users/workflow/jenkinsci-users/DJ15tkEQPw0")
    @Test
    public void noArgStep() throws Exception {
        st.assertRoundTrip(new PwdStep(), "pwd()");
    }

    @Test
    public void coreStep() throws Exception {
        ArtifactArchiver aa = new ArtifactArchiver("x.jar");
        aa.setAllowEmptyArchive(true);
        if (ArtifactArchiver.DescriptorImpl.class.isAnnotationPresent(Symbol.class)) {
            st.assertRoundTrip(new CoreStep(aa), "archiveArtifacts allowEmptyArchive: true, artifacts: 'x.jar'");
        } else { // TODO 2.x delete
            st.assertRoundTrip(
                    new CoreStep(aa),
                    "step([$class: 'ArtifactArchiver', allowEmptyArchive: true, artifacts: 'x.jar'])");
        }
    }

    @Test
    public void coreStep2() throws Exception {
        if (ArtifactArchiver.DescriptorImpl.class.isAnnotationPresent(Symbol.class)) {
            st.assertRoundTrip(new CoreStep(new ArtifactArchiver("x.jar")), "archiveArtifacts 'x.jar'");
        } else { // TODO 2.x delete
            st.assertRoundTrip(
                    new CoreStep(new ArtifactArchiver("x.jar")),
                    "step([$class: 'ArtifactArchiver', artifacts: 'x.jar'])");
        }
    }

    @Test
    public void recursiveSymbolUse() throws Exception {
        Island hawaii = new Island(new Island(new Island(), null), new Island());
        st.assertRoundTrip(
                new StateMetaStep(new Hawaii(hawaii)), "hawaii island(lhs: island(lhs: island()), rhs: island())");
    }

    @Test
    public void collisionWithStep() throws Exception {
        // this cannot use "or()" due to a collision with OrStep
        st.assertRoundTrip(new StateMetaStep(new Oregon()), "state([$class: 'Oregon'])");
    }

    @Test
    public void collisionWithAnotherMetaStep() throws Exception {
        // neither should produce "CO()" because that would prevent disambiguation
        st.assertRoundTrip(new StateMetaStep(new Colorado()), "state CO()");
        st.assertRoundTrip(new DetectionMetaStep(new CarbonMonoxide()), "detect CO()");
    }

    @Test
    public void blockSteps() throws Exception {
        st.assertRoundTrip(new ExecutorStep(null), "node {\n    // some block\n}");
        st.assertRoundTrip(new ExecutorStep("linux"), "node('linux') {\n    // some block\n}");
        st.assertRoundTrip(new WorkspaceStep(null), "ws {\n    // some block\n}");
        st.assertRoundTrip(new WorkspaceStep("loc"), "ws('loc') {\n    // some block\n}");
    }

    @Issue("JENKINS-29922")
    @Test
    public void blockMetaSteps() throws Exception {
        st.assertRoundTrip(new CurveMetaStep(new Circle()), "circle {\n    // some block\n}");
        st.assertRoundTrip(new CurveMetaStep(new Polygon(5)), "polygon(5) {\n    // some block\n}");
    }

    @Test
    public void escapes() throws Exception {
        st.assertRoundTrip(new EchoStep("Bob's message \\/ here"), "echo 'Bob\\'s message \\\\/ here'");
    }

    @Test
    public void multilineStrings() throws Exception {
        st.assertRoundTrip(
                new EchoStep("echo hello\necho 1/2 way\necho goodbye"),
                "echo '''echo hello\necho 1/2 way\necho goodbye'''");
    }

    @Issue("JENKINS-25779")
    @Test
    public void defaultValues() throws Exception {
        st.assertRoundTrip(new InputStep("Ready?"), "input 'Ready?'");
    }

    @Issue("JENKINS-29922")
    @Test
    public void getQuasiDescriptors() throws Exception {
        String quasiDescriptors = new Snippetizer().getQuasiDescriptors(false).toString();
        assertThat(quasiDescriptors, containsString("circle=Circle"));
        assertThat(quasiDescriptors, containsString("polygon=Polygon"));
        assertThat(quasiDescriptors, containsString("CO=CarbonMonoxide"));
        assertThat(
                "State.moderate currently disqualifies this metastep",
                quasiDescriptors,
                not(containsString("california=California")));
    }

    @Test
    public void generateSnippet() throws Exception {
        st.assertGenerateSnippet(
                "{'stapler-class':'" + EchoStep.class.getName() + "', 'message':'hello world'}",
                "echo 'hello world'",
                null);
        st.assertGenerateSnippet(
                "{'stapler-class':'" + Circle.class.getName() + "'}", "circle {\n    // some block\n}", null);
        st.assertGenerateSnippet(
                "{'stapler-class':'" + Polygon.class.getName() + "', 'n':5}",
                "polygon(5) {\n    // some block\n}",
                null);
        st.assertGenerateSnippet("{'stapler-class':'" + CarbonMonoxide.class.getName() + "'}", "detect CO()", null);
    }

    @Test
    public void generateSnippetAdvancedDeprecated() throws Exception {
        st.assertGenerateSnippet(
                "{'stapler-class':'" + AdvancedStep.class.getName() + "'}",
                "// " + Messages.Snippetizer_this_step_should_not_normally_be_used_in()
                        + "\nadvancedStuff {\n    // some block\n}",
                null);
    }

    public static final class AdvancedStep extends Step {
        @DataBoundConstructor
        public AdvancedStep() {}

        @Override
        public StepExecution start(StepContext context) throws Exception {
            throw new UnsupportedOperationException();
        }

        @TestExtension // cannot specify test name when using ClassRule
        public static final class DescriptorImpl extends StepDescriptor {
            @Override
            public String getFunctionName() {
                return "advancedStuff";
            }

            @Override
            public boolean isAdvanced() {
                return true;
            }

            @Override
            public boolean takesImplicitBlockArgument() {
                return true;
            }

            @Override
            public Set<? extends Class<?>> getRequiredContext() {
                return Collections.emptySet();
            }
        }
    }

    @Issue({"JENKINS-26126", "JENKINS-37215"})
    @Test
    public void doDslRef() throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        String html = wc.goTo(Snippetizer.ACTION_URL + "/html").getWebResponse().getContentAsString();
        assertThat("text from LoadStep/help-path.html is included", html, containsString("the Groovy file to load"));
        assertThat(
                "GitSCM.submoduleCfg is mentioned as an attribute of a value of GenericSCMStep.scm",
                html,
                containsString("submoduleCfg"));
        assertThat("CleanBeforeCheckout is mentioned as an option", html, containsString("CleanBeforeCheckout"));
        assertThat("content is written to the end", html, containsString("</body></html>"));
        assertThat("symbols are noted for heterogeneous lists", html, containsString("<code>booleanParam</code>"));
        assertThat("symbols are noted for homogeneous lists", html, containsString("<code>configFile</code>"));
    }

    @Issue({"JENKINS-35395", "JENKINS-38114"})
    @Test
    public void doGlobalsRef() throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        String html =
                wc.goTo(Snippetizer.ACTION_URL + "/globals").getWebResponse().getContentAsString();
        assertThat(
                "text from RunWrapperBinder/help.jelly is included",
                html,
                containsString("may be used to refer to the currently running build"));
        assertThat(
                "text from RunWrapperBinder/help.jelly includes text from RunWrapper/help.html",
                html,
                containsString("<dt><code>buildVariables</code></dt>"));
        assertThat("content is written to the end", html, containsString("</body></html>"));
    }

    @Issue("JENKINS-26126")
    @Test
    public void doGdsl() throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        String gdsl = wc.goTo(Snippetizer.ACTION_URL + "/gdsl", "text/plain")
                .getWebResponse()
                .getContentAsString();
        assertThat("Description is included as doc", gdsl, containsString("Shell Script"));
        assertThat("Timeout step appears", gdsl, containsString("name: 'timeout'"));

        // Verify valid groovy syntax.
        GroovyShell shell = new GroovyShell(r.jenkins.getPluginManager().uberClassLoader);
        shell.parse(gdsl);
    }

    @Issue("JENKINS-26126")
    @Test
    public void doDsld() throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        String dsld = wc.goTo(Snippetizer.ACTION_URL + "/dsld", "text/plain")
                .getWebResponse()
                .getContentAsString();
        assertThat("Description is included as doc", dsld, containsString("Shell Script"));
        assertThat("Timeout step appears", dsld, containsString("name: 'timeout'"));

        // Verify valid groovy sntax.
        GroovyShell shell = new GroovyShell(r.jenkins.getPluginManager().uberClassLoader);
        shell.parse(dsld);
    }

    @Issue("JENKINS-29711")
    @Test
    public void monomorphic() throws Exception {
        MonomorphicStep monomorphicStep = new MonomorphicStep(new MonomorphicData("one", "two"));
        st.assertRoundTrip(monomorphicStep, "monomorphStep([firstArg: 'one', secondArg: 'two'])");
    }

    @Issue("JENKINS-29711")
    @Test
    public void monomorphicList() throws Exception {
        List<MonomorphicData> dataList = new ArrayList<>();
        dataList.add(new MonomorphicData("one", "two"));
        dataList.add(new MonomorphicData("three", "four"));
        MonomorphicListStep monomorphicStep = new MonomorphicListStep(dataList);
        st.assertRoundTrip(
                monomorphicStep,
                "monomorphListStep([[firstArg: 'one', secondArg: 'two'], [firstArg: 'three', secondArg: 'four']])");
    }

    @Issue("JENKINS-29711")
    @Test
    public void monomorphicSymbol() throws Exception {
        MonomorphicWithSymbolStep monomorphicStep =
                new MonomorphicWithSymbolStep(new MonomorphicDataWithSymbol("one", "two"));
        st.assertRoundTrip(
                monomorphicStep, "monomorphWithSymbolStep monomorphSymbol(firstArg: 'one', secondArg: 'two')");
    }

    @Issue("JENKINS-29711")
    @Test
    public void monomorphicListSymbol() throws Exception {
        List<MonomorphicDataWithSymbol> dataList = new ArrayList<>();
        dataList.add(new MonomorphicDataWithSymbol("one", "two"));
        dataList.add(new MonomorphicDataWithSymbol("three", "four"));
        MonomorphicListWithSymbolStep monomorphicStep = new MonomorphicListWithSymbolStep(dataList);
        st.assertRoundTrip(
                monomorphicStep,
                "monomorphListSymbolStep([monomorphSymbol(firstArg: 'one', secondArg: 'two'), monomorphSymbol(firstArg: 'three', secondArg: 'four')])");
    }

    @Issue("JENKINS-34464")
    @Test
    public void resultRoundTrips() throws Exception {
        st.assertRoundTrip(new EchoResultStep(Result.UNSTABLE), "echoResult 'UNSTABLE'");
    }

    @Test
    public void noArgStepDocs() throws Exception {
        SnippetizerTester.assertDocGeneration(PwdStep.class);
    }

    @Test
    public void singleArgStepDocs() throws Exception {
        SnippetizerTester.assertDocGeneration(EchoStep.class);
    }

    @Test
    public void oneOrMoreArgsStepDocs() throws Exception {
        SnippetizerTester.assertDocGeneration(TimeoutStep.class);
    }

    @Test
    public void coreStepDocs() throws Exception {
        SnippetizerTester.assertDocGeneration(CoreStep.class);
    }

    /**
     * An example of a step that will fail to generate docs correctly due to a lack of a {@link org.kohsuke.stapler.DataBoundConstructor}.
     */
    @Test(expected = NoStaplerConstructorException.class)
    public void parallelStepDocs() throws Exception {
        SnippetizerTester.assertDocGeneration(ParallelStep.class);
    }

    @Issue("JENKINS-31967")
    @Test
    public void testStandardJavaTypes() throws Exception {
        EchoStringAndDoubleStep a = new EchoStringAndDoubleStep("some string");
        st.assertRoundTrip(a, "echoStringAndDouble 'some string'");
        a.setNumber(0.5);
        st.assertRoundTrip(a, "echoStringAndDouble number: 0.5, string: 'some string'");
    }

    @Test
    public void snippetizerLinks() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "p");
        JenkinsRule.WebClient wc = r.createWebClient();
        String html = wc.getPage(job, Snippetizer.ACTION_URL).getWebResponse().getContentAsString();
        assertThat(
                "Snippet Generator link is included",
                html,
                containsString("href=\"" + r.contextPath + "/" + job.getUrl() + Snippetizer.ACTION_URL + "\""));
        assertThat(
                "Steps Reference link is included",
                html,
                containsString("href=\"" + r.contextPath + "/" + job.getUrl() + Snippetizer.ACTION_URL + "/html\""));
        assertThat(
                "Globals Reference link is included",
                html,
                containsString("href=\"" + r.contextPath + "/" + job.getUrl() + Snippetizer.ACTION_URL + "/globals\""));
        assertThat("Online docs link is included", html, containsString("href=\"https://jenkins.io/doc/pipeline/\""));
        assertThat(
                "GDSL link is included",
                html,
                containsString("href=\"" + r.contextPath + "/" + job.getUrl() + Snippetizer.ACTION_URL + "/gdsl\""));

        // Now verify that the links are still present and correct when we're not within a job.
        String rootHtml = wc.goTo(Snippetizer.ACTION_URL).getWebResponse().getContentAsString();
        assertThat(
                "Snippet Generator link is included",
                rootHtml,
                containsString("href=\"" + r.contextPath + "/" + Snippetizer.ACTION_URL + "\""));
        assertThat(
                "Steps Reference link is included",
                rootHtml,
                containsString("href=\"" + r.contextPath + "/" + Snippetizer.ACTION_URL + "/html\""));
        assertThat(
                "Globals Reference link is included",
                rootHtml,
                containsString("href=\"" + r.contextPath + "/" + Snippetizer.ACTION_URL + "/globals\""));
        assertThat(
                "Online docs link is included", rootHtml, containsString("href=\"https://jenkins.io/doc/pipeline/\""));
        assertThat(
                "GDSL link is included",
                rootHtml,
                containsString("href=\"" + r.contextPath + "/" + Snippetizer.ACTION_URL + "/gdsl\""));
    }
}
