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

package org.jenkinsci.plugins.workflow.cps.replay;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.ExtensionPoint;
import java.util.Collections;
import java.util.Map;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;

/**
 * Defines which scripts are eligible to be replaced by {@link ReplayAction#run}.
 */
public abstract class OriginalLoadedScripts implements ExtensionPoint {

    /**
     * Finds scripts which are eligible for replacement.
     * @param execution a build
     * @return a map from Groovy class names to their original texts, as in {@link ReplayAction#replace}
     */
    public @NonNull Map<String, String> loadScripts(@NonNull CpsFlowExecution execution) {
        return Collections.emptyMap();
    }
}
