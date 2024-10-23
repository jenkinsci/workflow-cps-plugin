/*
 * The MIT License
 *
 * Copyright (c) 2024, CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps.config;

import java.io.IOException;
import java.io.UncheckedIOException;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.jenkinsci.Symbol;

/**
* @deprecated
 * This class hs been deprecated and we don't recommend to use it.
 * In order to force using the system sandbox for pipelines, please use the flag
 * org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval.get().isForceSandbox
 * or
 * org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval.get().forceSandboxForCurrentUser
 **/
@Symbol("cps")
@Extension
@Deprecated
public class CPSConfiguration extends GlobalConfiguration {

    /**
     * Whether to show the sandbox checkbox in jobs to users without Jenkins.ADMINISTER
     */
    private transient boolean hideSandbox;

    public CPSConfiguration() {

        load();
        if (hideSandbox) {
            ScriptApproval.get().setForceSandbox(hideSandbox);
        }

        //Data migration from this configuration to ScriptApproval should be done only once,
        //so removing the config file after the previous migration
        try {
            this.getConfigFile().delete();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public boolean isHideSandbox() {
        return ScriptApproval.get().isForceSandbox();
    }

    public void setHideSandbox(boolean hideSandbox) {
        this.hideSandbox = hideSandbox;
        ScriptApproval.get().setForceSandbox(hideSandbox);
        save();
    }

    @NonNull
    @Override
    public GlobalConfigurationCategory getCategory() {
        return GlobalConfigurationCategory.get(GlobalConfigurationCategory.Security.class);
    }

    public static CPSConfiguration get() {
        return ExtensionList.lookupSingleton(CPSConfiguration.class);
    }
}
