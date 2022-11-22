/*
 * The MIT License
 *
 * Copyright 2022 CloudBees, Inc.
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

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlCheckBoxInput;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextArea;
import hudson.cli.CLICommand;
import hudson.cli.CLICommandInvoker;
import hudson.cli.UpdateJobCommand;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.User;
import hudson.security.Permission;
import jenkins.model.Jenkins;
import org.apache.tools.ant.filters.StringInputStream;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.plugins.scriptsecurity.scripts.languages.GroovyLanguage;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.MockAuthorizationStrategy;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class CpsFlowDefinitionTest {

    @Rule public JenkinsRule jenkins = new JenkinsRule();

    @Issue("SECURITY-2450")
    @Test
    public void cpsScriptNonAdminConfiguration() throws Exception {
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());

        MockAuthorizationStrategy mockStrategy = new MockAuthorizationStrategy();
        mockStrategy.grant(Jenkins.READ).everywhere().to("devel");
        for (Permission p : Item.PERMISSIONS.getPermissions()) {
            mockStrategy.grant(p).everywhere().to("devel");
        }
        jenkins.jenkins.setAuthorizationStrategy(mockStrategy);

        JenkinsRule.WebClient wcDevel = jenkins.createWebClient();
        wcDevel.login("devel");

        WorkflowJob p = jenkins.createProject(WorkflowJob.class);

        HtmlForm config = wcDevel.getPage(p, "configure").getFormByName("config");
        List<HtmlTextArea> scripts = config.getTextAreasByName("_.script");
        // Get the last one, because previous ones might be from Lockable Resources during PCT.
        HtmlTextArea script = scripts.get(scripts.size() - 1);
        String groovy = "echo 'hi from cpsScriptNonAdminConfiguration'";
        script.setText(groovy);

        List<HtmlInput> sandboxes = config.getInputsByName("_.sandbox");
        // Get the last one, because previous ones might be from Lockable Resources during PCT.
        HtmlCheckBoxInput sandbox = (HtmlCheckBoxInput) sandboxes.get(sandboxes.size() - 1);
        assertTrue(sandbox.isChecked());
        sandbox.setChecked(false);

        jenkins.submit(config);
        assertEquals(1, ScriptApproval.get().getPendingScripts().size());
        assertFalse(ScriptApproval.get().isScriptApproved(groovy, GroovyLanguage.get()));
    }

    @Issue("SECURITY-2450")
    @Test
    public void cpsScriptAdminConfiguration() throws Exception {
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());

        MockAuthorizationStrategy mockStrategy = new MockAuthorizationStrategy();
        mockStrategy.grant(Jenkins.ADMINISTER).everywhere().to("admin");
        for (Permission p : Item.PERMISSIONS.getPermissions()) {
            mockStrategy.grant(p).everywhere().to("admin");
        }
        jenkins.jenkins.setAuthorizationStrategy(mockStrategy);

        JenkinsRule.WebClient admin = jenkins.createWebClient();
        admin.login("admin");

        WorkflowJob p = jenkins.createProject(WorkflowJob.class);

        HtmlForm config = admin.getPage(p, "configure").getFormByName("config");
        List<HtmlTextArea> scripts = config.getTextAreasByName("_.script");
        // Get the last one, because previous ones might be from Lockable Resources during PCT.
        HtmlTextArea script = scripts.get(scripts.size() - 1);
        String groovy = "echo 'hi from cpsScriptAdminConfiguration'";
        script.setText(groovy);

        List<HtmlInput> sandboxes = config.getInputsByName("_.sandbox");
        // Get the last one, because previous ones might be from Lockable Resources during PCT.
        HtmlCheckBoxInput sandbox = (HtmlCheckBoxInput) sandboxes.get(sandboxes.size() - 1);
        assertTrue(sandbox.isChecked());
        sandbox.setChecked(false);

        jenkins.submit(config);

        assertTrue(ScriptApproval.get().isScriptApproved(groovy, GroovyLanguage.get()));
    }

    @Issue("SECURITY-2450")
    @Test
    public void cpsScriptAdminModification() throws Exception {
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());

        MockAuthorizationStrategy mockStrategy = new MockAuthorizationStrategy();
        mockStrategy.grant(Jenkins.READ).everywhere().to("devel");
        mockStrategy.grant(Jenkins.ADMINISTER).everywhere().to("admin");
        for (Permission p : Item.PERMISSIONS.getPermissions()) {
            mockStrategy.grant(p).everywhere().to("devel");
            mockStrategy.grant(p).everywhere().to("admin");
        }
        jenkins.jenkins.setAuthorizationStrategy(mockStrategy);

        JenkinsRule.WebClient wc = jenkins.createWebClient();
        wc.login("devel");

        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        String userGroovy = "echo 'hi from devel'";
        String adminGroovy = "echo 'hi from admin'";

        // initial configuration by user, script ends up in pending
        {
            HtmlForm config = wc.getPage(p, "configure").getFormByName("config");
            List<HtmlTextArea> scripts = config.getTextAreasByName("_.script");
            // Get the last one, because previous ones might be from Lockable Resources during PCT.
            HtmlTextArea script = scripts.get(scripts.size() - 1);
            script.setText(userGroovy);

            List<HtmlInput> sandboxes = config.getInputsByName("_.sandbox");
            // Get the last one, because previous ones might be from Lockable Resources during PCT.
            HtmlCheckBoxInput sandbox = (HtmlCheckBoxInput) sandboxes.get(sandboxes.size() - 1);
            assertTrue(sandbox.isChecked());
            sandbox.setChecked(false);

            jenkins.submit(config);

            assertEquals(1, ScriptApproval.get().getPendingScripts().size());

            assertFalse(ScriptApproval.get().isScriptApproved(userGroovy, GroovyLanguage.get()));
        }

        wc.login("admin");

        // modification by admin, script gets approved automatically
        {
            HtmlForm config = wc.getPage(p, "configure").getFormByName("config");
            List<HtmlTextArea> scripts = config.getTextAreasByName("_.script");
            // Get the last one, because previous ones might be from Lockable Resources during PCT.
            HtmlTextArea script = scripts.get(scripts.size() - 1);
            script.setText(adminGroovy);

            List<HtmlInput> sandboxes = config.getInputsByName("_.sandbox");
            // Get the last one, because previous ones might be from Lockable Resources during PCT.
            HtmlCheckBoxInput sandbox = (HtmlCheckBoxInput) sandboxes.get(sandboxes.size() - 1);
            assertFalse(sandbox.isChecked());

            jenkins.submit(config);

            // script content was modified by admin, so it should be approved upon save
            // the one that had been submitted by the user previously stays in pending
            assertTrue(ScriptApproval.get().isScriptApproved(adminGroovy, GroovyLanguage.get()));
            assertFalse(ScriptApproval.get().isScriptApproved(userGroovy, GroovyLanguage.get()));
        }
    }

    @Test
    public void cpsScriptSubmissionViaCli() throws Exception {
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());

        MockAuthorizationStrategy mockStrategy = new MockAuthorizationStrategy();
        mockStrategy.grant(Jenkins.READ, Job.CONFIGURE).everywhere().to("devel");
        mockStrategy.grant(Jenkins.ADMINISTER).everywhere().to("admin");
        for (Permission p : Item.PERMISSIONS.getPermissions()) {
            mockStrategy.grant(p).everywhere().to("devel");
            mockStrategy.grant(p).everywhere().to("admin");
        }
        jenkins.jenkins.setAuthorizationStrategy(mockStrategy);

        WorkflowJob p = jenkins.createProject(WorkflowJob.class, "prj");
        String preconfiguredScript = "echo preconfigured";
        ScriptApproval.get().preapprove(preconfiguredScript, GroovyLanguage.get());
        p.setDefinition(new CpsFlowDefinition(preconfiguredScript, false));

        JenkinsRule.WebClient wc = jenkins.createWebClient();
        wc.login("admin");
        String configDotXml = p.getUrl() + "config.xml";
        String xml = wc.goTo(configDotXml, "application/xml").getWebResponse().getContentAsString();

        CLICommand cmd = new UpdateJobCommand();
        cmd.setTransportAuth2(User.getById("admin", true).impersonate2());
        String viaCliScript = "echo configured via CLI";
        assertThat(new CLICommandInvoker(jenkins, cmd).withStdin(new StringInputStream(xml.replace(preconfiguredScript, viaCliScript))).invokeWithArgs(p.getName()), CLICommandInvoker.Matcher.succeededSilently());
        assertEquals(viaCliScript, ((CpsFlowDefinition)p.getDefinition()).getScript());
        assertTrue(ScriptApproval.get().isScriptApproved(viaCliScript, GroovyLanguage.get()));

        // now with non-admin user, script should end up in pending
        cmd.setTransportAuth2(User.getById("devel", true).impersonate2());
        String viaCliByDevelScript = "echo configured via CLI by devel";
        assertThat(new CLICommandInvoker(jenkins, cmd).withStdin(new StringInputStream(xml.replace(preconfiguredScript, viaCliByDevelScript))).invokeWithArgs(p.getName()), CLICommandInvoker.Matcher.succeededSilently());
        assertEquals(viaCliByDevelScript, ((CpsFlowDefinition)p.getDefinition()).getScript());
        assertFalse(ScriptApproval.get().isScriptApproved(viaCliByDevelScript, GroovyLanguage.get()));
        wc.close();
    }

    @Test
    public void cpsScriptSubmissionViaRest() throws Exception {
        jenkins.jenkins.setSecurityRealm(jenkins.createDummySecurityRealm());

        MockAuthorizationStrategy mockStrategy = new MockAuthorizationStrategy();
        mockStrategy.grant(Jenkins.READ, Job.CONFIGURE).everywhere().to("devel");
        mockStrategy.grant(Jenkins.ADMINISTER).everywhere().to("admin");
        for (Permission p : Item.PERMISSIONS.getPermissions()) {
            mockStrategy.grant(p).everywhere().to("devel");
            mockStrategy.grant(p).everywhere().to("admin");
        }
        jenkins.jenkins.setAuthorizationStrategy(mockStrategy);

        WorkflowJob p = jenkins.createProject(WorkflowJob.class);
        String preconfiguredScript = "echo preconfigured";
        ScriptApproval.get().preapprove(preconfiguredScript, GroovyLanguage.get());
        p.setDefinition(new CpsFlowDefinition(preconfiguredScript, false));

        JenkinsRule.WebClient wc = jenkins.createWebClient();
        wc.login("admin");
        String configDotXmlUrl = p.getUrl() + "config.xml";
        String xml = wc.goTo(configDotXmlUrl, "application/xml").getWebResponse().getContentAsString();

        WebRequest req = new WebRequest(wc.createCrumbedUrl(configDotXmlUrl), HttpMethod.POST);
        req.setEncodingType(null);
        String configuredViaRestScript = "echo configured via REST";
        req.setRequestBody(xml.replace(preconfiguredScript, configuredViaRestScript));
        wc.getPage(req);
        assertEquals(configuredViaRestScript, ((CpsFlowDefinition)p.getDefinition()).getScript());
        assertTrue(ScriptApproval.get().isScriptApproved(configuredViaRestScript, GroovyLanguage.get()));

        wc.login("devel");
        String configuredViaRestByNonAdmin = "echo configured via REST by devel";
        req.setRequestBody(xml.replace(preconfiguredScript, configuredViaRestByNonAdmin));
        wc.getPage(req);
        assertEquals(configuredViaRestByNonAdmin, ((CpsFlowDefinition)p.getDefinition()).getScript());
        assertFalse(ScriptApproval.get().isScriptApproved(configuredViaRestByNonAdmin, GroovyLanguage.get()));
        wc.close();
    }
}
