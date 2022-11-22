/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
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

import hudson.model.Action;
import java.io.IOException;
import org.kohsuke.stapler.interceptor.RequirePOST;

/**
 * Allows a running flow to be paused or unpaused.
 */
public class PauseUnpauseAction implements Action {

    static final String URL = "pause";

    private final CpsFlowExecution execution;

    PauseUnpauseAction(CpsFlowExecution execution) {
        this.execution = execution;
    }

    @Override public String getUrlName() {
        return URL;
    }

    @Override public String getIconFileName() {
        return null; // special presentation
    }

    @Override public String getDisplayName() {
        return null; // special presentation
    }

    @RequirePOST
    public void doToggle() throws IOException {
        execution.pause(!execution.isPaused());
    }

}
