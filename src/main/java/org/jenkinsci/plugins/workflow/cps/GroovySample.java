/*
 * The MIT License
 *
 * Copyright 2020 CloudBees, Inc.
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

import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.InvisibleAction;
import hudson.model.RootAction;
import java.io.IOException;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * A sample Pipeline Groovy script to appear in the {@code <wfe:workflow-editor/>}.
 */
public interface GroovySample extends ExtensionPoint {

    String name();

    String title();

    String script();

    @Restricted(DoNotUse.class)
    @Extension final class Registry implements RootAction {

        @Override public String getIconFileName() {
            return null;
        }

        @Override public String getDisplayName() {
            return null;
        }

        @Override public String getUrlName() {
            return "workflow-cps-samples";
        }

        public JSONObject doIndex() {
            JSONArray samples = new JSONArray();
            ExtensionList.lookup(GroovySample.class).forEach(gs -> samples.add(new JSONObject().accumulate("name", gs.name()).accumulate("title", gs.title()).accumulate("script", gs.script())));
            return new JSONObject().accumulate("samples", samples);
        }

    }

    @Restricted(NoExternalUse.class)
    abstract class Static implements GroovySample {
        
        @Override public String script() {
            try {
                return IOUtils.toString(GroovySample.class.getResource("samples/" + name() + ".groovy"));
            } catch (IOException x) {
                throw new AssertionError(x);
            }
        }

    }

    // TODO move to pipeline-model-definition
    @Restricted(DoNotUse.class)
    @Extension(ordinal = 300) final class Hello extends Static {

        @Override public String name() {
            return "hello";
        }

        @Override public String title() {
            return "Hello World";
        }

    }

    // TODO move to pipeline-model-definition
    @Restricted(DoNotUse.class)
    @Extension(ordinal = 200) final class GitHubMaven extends Static {

        @Override public String name() {
            return "github-maven";
        }

        @Override public String title() {
            return "GitHub + Maven";
        }

    }

    @Restricted(DoNotUse.class)
    @Extension(ordinal = 100) final class Scripted extends Static {

        @Override public String name() {
            return "scripted";
        }

        @Override public String title() {
            return "Scripted Pipeline";
        }

    }

}
