package org.jenkinsci.plugins.workflow.cps.view;

import hudson.model.Run;
import jenkins.model.RunAction2;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Action to generate the UI report for watched environment variables
 */
public class InterpolatedSecretsAction implements RunAction2 {

    private Set<String> results;
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

    public void record(List<String> stepResults) {
        if (results == null) {
            results = new HashSet<>();
        }
        results.addAll(stepResults);
    }

    public Set<String> getResults() {
        return results;
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
