package org.jenkinsci.plugins.workflow.cps.view;

import hudson.model.Run;
import jenkins.model.RunAction2;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Action to generate the UI report for watched environment variables
 */
public class InterpolatedSecretsAction implements RunAction2 {

    private List<List<Object>> interpolatedWarnings;
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

    public void record(@Nonnull String stepName, @Nonnull Map<String, List<String>> stepArguments) {
        if (interpolatedWarnings == null) {
            interpolatedWarnings = new ArrayList<>();
        }
        interpolatedWarnings.add(Arrays.asList(stepName, stepArguments));
    }

    public List<List<Object>> getWarnings() {
        return interpolatedWarnings;
    }

    public boolean getInProgress() {
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
}
