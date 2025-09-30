/*
 * The MIT License
 *
 * Copyright (c) 2020, CloudBees, Inc.
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
package org.jenkinsci.plugins.workflow.cps.view;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.model.Run;
import java.util.ArrayList;
import java.util.List;
import jenkins.model.RunAction2;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

/**
 * Action to generate the UI report for watched environment variables
 */
@Restricted(NoExternalUse.class)
@ExportedBean
public class InterpolatedSecretsAction implements RunAction2 {

    private List<InterpolatedWarnings> interpolatedWarnings = new ArrayList<>();
    private transient Run<?, ?> run;

    public String getIconFileName() {
        return null;
    }

    public String getDisplayName() {
        return null;
    }

    public String getUrlName() {
        return null;
    }

    public void record(@NonNull String stepName, @NonNull List<String> interpolatedVariables) {
        interpolatedWarnings.add(new InterpolatedWarnings(stepName, interpolatedVariables));
    }

    @Exported
    public List<InterpolatedWarnings> getWarnings() {
        return interpolatedWarnings;
    }

    public boolean hasWarnings() {
        if (interpolatedWarnings.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    public boolean isInProgress() {
        return run.isBuilding();
    }

    @Override
    public void onAttached(Run<?, ?> run) {
        this.run = run;
    }

    @Override
    public void onLoad(Run<?, ?> run) {
        this.run = run;
    }

    @ExportedBean
    public static class InterpolatedWarnings {
        final String stepName;
        final List<String> interpolatedVariables;

        InterpolatedWarnings(@NonNull String stepName, @NonNull List<String> interpolatedVariables) {
            this.stepName = stepName;
            this.interpolatedVariables = interpolatedVariables;
        }

        @Exported
        public String getStepName() {
            return stepName;
        }

        @Exported
        public List<String> getInterpolatedVariables() {
            return interpolatedVariables;
        }
    }
}
