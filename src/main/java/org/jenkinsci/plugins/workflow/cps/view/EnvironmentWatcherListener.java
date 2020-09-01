package org.jenkinsci.plugins.workflow.cps.view;

import hudson.Extension;
import hudson.model.Run;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionListener;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.logging.Logger;

/**
 * Listener to add action for UI report for each run
 */
@Extension
public class EnvironmentWatcherListener extends FlowExecutionListener {
    private static final Logger LOGGER = Logger.getLogger(EnvironmentWatcherListener.class.getName());

    @Override
    public void onRunning(@Nonnull FlowExecution execution) {
        FlowExecutionOwner owner = execution.getOwner();
        try {
            if (owner != null && owner.getExecutable() instanceof Run) {
                ((Run) owner.getExecutable()).addAction(new EnvironmentWatcherRunReport());
            }
        } catch (IOException e) {
            LOGGER.warning(e.getMessage());
        }
    }
}
