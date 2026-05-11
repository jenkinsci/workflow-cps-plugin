/*
 * The MIT License
 *
 * Copyright 2026 CloudBees, Inc.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.startsWith;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.Options;
import com.microsoft.playwright.junit.OptionsFactory;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.options.AriaRole;
import hudson.ExtensionList;
import java.util.List;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.GroovySample;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@UsePlaywright(WorkflowEditorTest.HeadlessOptionsFactory.class)
@WithJenkins
class WorkflowEditorTest {

    @DisabledOnOs(OS.WINDOWS) // TODO https://github.com/jenkinsci/workflow-cps-plugin/pull/1775#discussion_r3220669617
    @Test
    void smokes(JenkinsRule r, Page page) throws Exception {
        ExtensionList.lookupSingleton(CpsFlowDefinition.DescriptorImpl.class).enableWorkflowEditor = true;
        var p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("", true));

        // Configure project, select sample, focus editor
        page.context().grantPermissions(List.of("clipboard-read", "clipboard-write"));
        page.navigate(p.getAbsoluteUrl() + "configure");
        page.locator("#workflow-editor-samples")
                .selectOption(ExtensionList.lookupSingleton(GroovySample.Scripted.class)
                        .name());
        page.locator(".ace_content").click();
        var textbox = page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Cursor at row"));

        // Cut the sample to clipboard
        textbox.press("ControlOrMeta+a");
        textbox.press("ControlOrMeta+x");
        assertThat(
                "looks like scripted.groovy",
                (String) page.evaluate("navigator.clipboard.readText()"),
                startsWith("node {"));

        // Type "node {\n" again (character by character, not .fill())
        textbox.pressSequentially("node {");
        textbox.press("Enter");

        // Type "sh 'echo hello" (without closing quote to test auto-completion)
        textbox.pressSequentially("sh 'echo hello");

        // Select all and copy
        textbox.press("ControlOrMeta+a");
        textbox.press("ControlOrMeta+c");

        assertThat(
                "clipboard contains the formatted code with proper indentation",
                (String) page.evaluate("navigator.clipboard.readText()"),
                equalTo("""
            node {
                sh 'echo hello'
            }"""));
    }

    public static final class HeadlessOptionsFactory implements OptionsFactory {
        @Override
        public Options getOptions() {
            return new Options().setHeadless(Boolean.valueOf(System.getenv("CI")));
        }
    }
}
