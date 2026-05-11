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

import com.microsoft.playwright.Page;
import com.microsoft.playwright.junit.Options;
import com.microsoft.playwright.junit.OptionsFactory;
import com.microsoft.playwright.junit.UsePlaywright;
import com.microsoft.playwright.options.AriaRole;
import hudson.ExtensionList;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@UsePlaywright(WorkflowEditorTest.HeadlessOptionsFactory.class)
@WithJenkins
class WorkflowEditorTest {

    @Test
    void xxx(JenkinsRule r, Page page) throws Exception {
        ExtensionList.lookupSingleton(CpsFlowDefinition.DescriptorImpl.class).enableWorkflowEditor = true;
        var p = r.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("", true));
        page.navigate(p.getAbsoluteUrl() + "configure");
        page.getByRole(AriaRole.COMBOBOX).nth(1).selectOption("scripted");
        page.locator(".ace_content").click();
        page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Cursor at row"))
                .press("ControlOrMeta+a");
        page.getByRole(AriaRole.TEXTBOX, new Page.GetByRoleOptions().setName("Cursor at row"))
                .press("ControlOrMeta+x");
        // TODO assert that clipboard starts with "node {"
        // TODO now type "node {\n" again (do *not* use .fill(String)), then "sh 'echo hello",
        // and C-A C-C assert clipboard contains "node {\n    sh 'echo hello'\n}"
    }

    public static final class HeadlessOptionsFactory implements OptionsFactory {
        @Override
        public Options getOptions() {
            return new Options().setHeadless(Boolean.valueOf(System.getenv("CI")));
        }
    }
}
