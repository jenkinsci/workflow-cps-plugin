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

import groovy.lang.GroovyShell;
import hudson.model.BooleanParameterDefinition;
import hudson.model.BooleanParameterValue;
import hudson.model.FreeStyleProject;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.StringParameterDefinition;
import hudson.model.StringParameterValue;
import hudson.tasks.ArtifactArchiver;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.workflow.steps.CatchErrorStep;
import org.jenkinsci.plugins.workflow.steps.CoreStep;
import org.jenkinsci.plugins.workflow.steps.EchoStep;
import org.jenkinsci.plugins.workflow.steps.PwdStep;
import org.jenkinsci.plugins.workflow.support.steps.ExecutorStep;
import org.jenkinsci.plugins.workflow.support.steps.StageStep;
import org.jenkinsci.plugins.workflow.support.steps.WorkspaceStep;
import org.jenkinsci.plugins.workflow.support.steps.build.BuildTriggerStep;
import org.jenkinsci.plugins.workflow.support.steps.input.InputStep;
import org.jenkinsci.plugins.workflow.testMetaStep.Colorado;
import org.jenkinsci.plugins.workflow.testMetaStep.Hawaii;
import org.jenkinsci.plugins.workflow.testMetaStep.Island;
import org.jenkinsci.plugins.workflow.testMetaStep.Oregon;
import org.jenkinsci.plugins.workflow.testMetaStep.StateMetaStep;
import org.jenkinsci.plugins.workflow.testMetaStep.chemical.CarbonMonoxide;
import org.jenkinsci.plugins.workflow.testMetaStep.chemical.DetectionMetaStep;
import org.junit.ClassRule;
import org.junit.Test;
import org.jvnet.hudson.test.Email;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockFolder;

import java.util.Arrays;
import java.util.logging.Level;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.*;
import org.jvnet.hudson.test.LoggerRule;

// TODO these tests would better be moved to the respective plugins

public class SnippetizerTest {

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @ClassRule
    public static LoggerRule logging = new LoggerRule().record(DescribableModel.class, Level.ALL);

    private static SnippetizerTester st = new SnippetizerTester(r);

    @Test public void basics() throws Exception {
        st.assertRoundTrip(new EchoStep("hello world"), "echo 'hello world'");
        StageStep s = new StageStep("Build");
        st.assertRoundTrip(s, "stage 'Build'");
        s.concurrency = 1;
        st.assertRoundTrip(s, "stage concurrency: 1, name: 'Build'");
    }

    @Email("https://groups.google.com/forum/#!topicsearchin/jenkinsci-users/workflow/jenkinsci-users/DJ15tkEQPw0")
    @Test public void noArgStep() throws Exception {
        st.assertRoundTrip(new PwdStep(), "pwd()");
    }

    @Test public void coreStep() throws Exception {
        ArtifactArchiver aa = new ArtifactArchiver("x.jar");
        aa.setAllowEmptyArchive(true);
        if (ArtifactArchiver.DescriptorImpl.class.isAnnotationPresent(Symbol.class)) {
            st.assertRoundTrip(new CoreStep(aa), "step archiveArtifacts(allowEmptyArchive: true, artifacts: 'x.jar')");
        } else { // TODO 2.x delete
            st.assertRoundTrip(new CoreStep(aa), "step([$class: 'ArtifactArchiver', allowEmptyArchive: true, artifacts: 'x.jar'])");
        }
    }

    @Test public void coreStep2() throws Exception {
        if (ArtifactArchiver.DescriptorImpl.class.isAnnotationPresent(Symbol.class)) {
            st.assertRoundTrip(new CoreStep(new ArtifactArchiver("x.jar")), "step archiveArtifacts('x.jar')");
        } else { // TODO 2.x delete
            st.assertRoundTrip(new CoreStep(new ArtifactArchiver("x.jar")), "step([$class: 'ArtifactArchiver', artifacts: 'x.jar'])");
        }
    }

    @Test public void recursiveSymbolUse() throws Exception {
        Island hawaii = new Island(new Island(new Island(),null),new Island());
        st.assertRoundTrip(new StateMetaStep(new Hawaii(hawaii)), "hawaii island(lhs: island(lhs: island()), rhs: island())");
    }

    @Test public void collisionWithStep() throws Exception {
        // this cannot use "or()" due to a collision with OrStep
        st.assertRoundTrip(new StateMetaStep(new Oregon()), "state([$class: 'Oregon'])");
    }

    @Test public void collisionWithAnotherMetaStep() throws Exception {
        // neither should produce "CO()" because that would prevent disambiguation
        st.assertRoundTrip(new StateMetaStep(new Colorado()), "state CO()");
        st.assertRoundTrip(new DetectionMetaStep(new CarbonMonoxide()), "detect CO()");
    }

    @Test public void blockSteps() throws Exception {
        st.assertRoundTrip(new ExecutorStep(null), "node {\n    // some block\n}");
        st.assertRoundTrip(new ExecutorStep("linux"), "node('linux') {\n    // some block\n}");
        st.assertRoundTrip(new WorkspaceStep(null), "ws {\n    // some block\n}");
        st.assertRoundTrip(new WorkspaceStep("loc"), "ws('loc') {\n    // some block\n}");
    }

    @Test public void escapes() throws Exception {
        st.assertRoundTrip(new EchoStep("Bob's message \\/ here"), "echo 'Bob\\'s message \\\\/ here'");
    }

    @Test public void multilineStrings() throws Exception {
        st.assertRoundTrip(new EchoStep("echo hello\necho 1/2 way\necho goodbye"), "echo '''echo hello\necho 1/2 way\necho goodbye'''");
    }

    @Test public void buildTriggerStep() throws Exception {
        BuildTriggerStep step = new BuildTriggerStep("downstream");
        st.assertRoundTrip(step, "build 'downstream'");
        step.setParameters(Arrays.asList(new StringParameterValue("branch", "default"), new BooleanParameterValue("correct", true)));
        if (StringParameterDefinition.DescriptorImpl.class.isAnnotationPresent(Symbol.class)) {
            st.assertRoundTrip(step, "build job: 'downstream', parameters: [string(name: 'branch', value: 'default'), booleanParam(name: 'correct', value: true)]");
        } else { // TODO 2.x delete
            st.assertRoundTrip(step, "build job: 'downstream', parameters: [[$class: 'StringParameterValue', name: 'branch', value: 'default'], [$class: 'BooleanParameterValue', name: 'correct', value: true]]");
        }
    }

    @Issue("JENKINS-25779")
    @Test public void defaultValues() throws Exception {
        st.assertRoundTrip(new InputStep("Ready?"), "input 'Ready?'");
    }

    @Test public void generateSnippet() throws Exception {
        st.assertGenerateSnippet("{'stapler-class':'" + EchoStep.class.getName() + "', 'message':'hello world'}", "echo 'hello world'", null);
    }

    @Issue("JENKINS-26093")
    @Test public void generateSnippetForBuildTrigger() throws Exception {
        MockFolder d1 = r.createFolder("d1");
        FreeStyleProject ds = d1.createProject(FreeStyleProject.class, "ds");
        MockFolder d2 = r.createFolder("d2");
        // Really this would be a WorkflowJob, but we cannot depend on that here, and it should not matter since we are just looking for Job:
        FreeStyleProject us = d2.createProject(FreeStyleProject.class, "us");
        ds.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("key", ""), new BooleanParameterDefinition("flag", false, "")));
        String snippet;
        if (StringParameterDefinition.DescriptorImpl.class.isAnnotationPresent(Symbol.class)) {
            snippet = "build job: '../d1/ds', parameters: [string(name: 'key', value: 'stuff'), booleanParam(name: 'flag', value: true)]";
        } else { // TODO 2.x delete
            snippet = "build job: '../d1/ds', parameters: [[$class: 'StringParameterValue', name: 'key', value: 'stuff'], [$class: 'BooleanParameterValue', name: 'flag', value: true]]";
        }
        st.assertGenerateSnippet("{'stapler-class':'" + BuildTriggerStep.class.getName() + "', 'job':'../d1/ds', 'parameter': [{'name':'key', 'value':'stuff'}, {'name':'flag', 'value':true}]}", snippet, us.getAbsoluteUrl() + "configure");
    }

    @Issue("JENKINS-29739")
    @Test public void generateSnippetForBuildTriggerSingle() throws Exception {
        FreeStyleProject ds = r.jenkins.createProject(FreeStyleProject.class, "ds1");
        FreeStyleProject us = r.jenkins.createProject(FreeStyleProject.class, "us1");
        ds.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("key", "")));
        String snippet;
        if (StringParameterDefinition.DescriptorImpl.class.isAnnotationPresent(Symbol.class)) {
            snippet = "build job: 'ds1', parameters: [string(name: 'key', value: 'stuff')]";
        } else { // TODO 2.x delete
            snippet = "build job: 'ds1', parameters: [[$class: 'StringParameterValue', name: 'key', value: 'stuff']]";
        }
        st.assertGenerateSnippet("{'stapler-class':'" + BuildTriggerStep.class.getName() + "', 'job':'ds1', 'parameter': {'name':'key', 'value':'stuff'}}", snippet, us.getAbsoluteUrl() + "configure");
    }

    @Test public void generateSnippetForBuildTriggerNone() throws Exception {
        FreeStyleProject ds = r.jenkins.createProject(FreeStyleProject.class, "ds0");
        FreeStyleProject us = r.jenkins.createProject(FreeStyleProject.class, "us0");
        st.assertGenerateSnippet("{'stapler-class':'" + BuildTriggerStep.class.getName() + "', 'job':'ds0'}", "build 'ds0'", us.getAbsoluteUrl() + "configure");
    }

    @Test public void generateSnippetAdvancedDeprecated() throws Exception {
        st.assertGenerateSnippet("{'stapler-class':'" + CatchErrorStep.class.getName() + "'}", "// " + Messages.Snippetizer_this_step_should_not_normally_be_used_in() + "\ncatchError {\n    // some block\n}", null);
    }

    @Issue("JENKINS-26126")
    @Test public void doDslRef() throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        String html = wc.goTo(Snippetizer.ACTION_URL + "/html").getWebResponse().getContentAsString();
        assertThat("text from LoadStep/help-path.html is included", html, containsString("the Groovy file to load"));
        assertThat("SubversionSCM.workspaceUpdater is mentioned as an attribute of a value of GenericSCMStep.delegate", html, containsString("workspaceUpdater"));
        assertThat("CheckoutUpdater is mentioned as an option", html, containsString("CheckoutUpdater"));
        assertThat("content is written to the end", html, containsString("</body></html>"));
    }

    @Issue("JENKINS-35395")
    @Test public void doGlobalsRef() throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        String html = wc.goTo(Snippetizer.ACTION_URL + "/globals").getWebResponse().getContentAsString();
        assertThat("text from RunWrapperBinder/help.jelly is included", html, containsString("may be used to refer to the currently running build"));
        assertThat("content is written to the end", html, containsString("</body></html>"));
    }

    @Issue("JENKINS-26126")
    @Test public void doGdsl() throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        String gdsl = wc.goTo(Snippetizer.ACTION_URL + "/gdsl", "text/plain").getWebResponse().getContentAsString();
        assertThat("Description is included as doc", gdsl, containsString("Build a job"));
        assertThat("Timeout step appears", gdsl, containsString("name: 'timeout'"));

        // Verify valid groovy syntax.
        GroovyShell shell = new GroovyShell(r.jenkins.getPluginManager().uberClassLoader);
        shell.parse(gdsl);
    }

    @Issue("JENKINS-26126")
    @Test public void doDsld() throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        String dsld = wc.goTo(Snippetizer.ACTION_URL + "/dsld", "text/plain").getWebResponse().getContentAsString();
        assertThat("Description is included as doc", dsld, containsString("Build a job"));
        assertThat("Timeout step appears", dsld, containsString("name: 'timeout'"));

        // Verify valid groovy sntax.
        GroovyShell shell = new GroovyShell(r.jenkins.getPluginManager().uberClassLoader);
        shell.parse(dsld);
    }
}
