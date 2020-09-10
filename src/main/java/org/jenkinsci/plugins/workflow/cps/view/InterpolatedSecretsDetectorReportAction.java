package org.jenkinsci.plugins.workflow.cps.view;

import hudson.Extension;
import hudson.model.Run;
import jenkins.model.RunAction2;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionListener;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Action to generate the UI report for watched environment variables
 */
public class InterpolatedSecretsDetectorReportAction implements RunAction2 {

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

    /**
     * Listener to add action for UI report for each run
     */
    @Extension
    public static class EnvironmentWatcherListener extends FlowExecutionListener {
        private static final Logger LOGGER = Logger.getLogger(InterpolatedSecretsDetectorReportAction.EnvironmentWatcherListener.class.getName());

        @Override
        public void onRunning(@Nonnull FlowExecution execution) {
            FlowExecutionOwner owner = execution.getOwner();
            try {
                if (owner != null && owner.getExecutable() instanceof Run) {
                    ((Run) owner.getExecutable()).addAction(new InterpolatedSecretsDetectorReportAction());
                }
            } catch (IOException e) {
                LOGGER.warning(e.getMessage());
            }
        }
    }

    private static final long serialVersionUID = 1L;
}
