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

import org.jenkinsci.Symbol;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.ExtensionList;
import jenkins.model.GlobalConfiguration;
import jenkins.model.GlobalConfigurationCategory;

@Symbol("cps")
@Extension
public class CPSConfiguration extends GlobalConfiguration {

    /**
     * Whether to show the sandbox checkbox in jobs to users without Jenkins.ADMINISTER
     */
    private boolean hideSandbox;
	private boolean pipelinesPausingWhenQueitingDown = true;
	private boolean forcefullyStopBuldsAfterTimeout = false;
	private int buildTerminationTimeoutMinutes;
	
    public CPSConfiguration() {
        load();
    }

    public boolean isHideSandbox() {
        return hideSandbox;
    }

    public void setHideSandbox(boolean hideSandbox) {
        this.hideSandbox = hideSandbox;
        save();
    }
    
    public boolean isPipelinesPausingWhenQueitingDown() {
		return pipelinesPausingWhenQueitingDown;
	}
	
	public void setPipelinesPausingWhenQueitingDown(boolean enabled) {
		this.pipelinesPausingWhenQueitingDown = enabled;
		save();
	}
	
	public boolean isForcefullyStopBuldsAfterTimeout() {
		return forcefullyStopBuldsAfterTimeout;
	}
	
	public void setForcefullyStopBuldsAfterTimeout(boolean stop) {
		this.forcefullyStopBuldsAfterTimeout = stop;
		save();
	}
	
	public int getBuildTerminationTimeoutMinutes() {
		return buildTerminationTimeoutMinutes;
	}
	
	public void setBuildTerminationTimeoutMinutes(int delay) {
		this.buildTerminationTimeoutMinutes = delay;
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
