package org.jenkinsci.plugins.workflow.cps;

import hudson.EnvVars;
import hudson.model.Run;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.view.InterpolatedSecretsDetectorReportAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class InterpolatedSecretsDetector {
    private EnvVars envVars;
    private Set<String> watchedVars;
    private List<String> scanResults;
    private static InterpolatedSecretsDetectorReportAction runReport;

    private static final Logger LOGGER = Logger.getLogger(InterpolatedSecretsDetector.class.getName());

    public static InterpolatedSecretsDetector of(CpsStepContext context, CpsFlowExecution exec) {
        try {
            EnvVars contextEnvVars = context.get(EnvVars.class);
            EnvironmentExpander contextExpander = context.get(EnvironmentExpander.class);
            if (contextEnvVars != null && contextExpander != null) {
                if (runReport == null) {
                    FlowExecutionOwner owner = exec.getOwner();
                    if (owner != null && owner.getExecutable() instanceof Run) {
                        runReport = ((Run) owner.getExecutable()).getAction(InterpolatedSecretsDetectorReportAction.class);
                    }
                }
                if (runReport != null) {
                    return new InterpolatedSecretsDetector(contextEnvVars, contextExpander);
                }
            }
        } catch (InterruptedException | IOException e) {
            LOGGER.log(Level.FINE, "Unable to create EnvironmentWatcher instance.");
        }
        return null;
    }

    public InterpolatedSecretsDetector(@Nonnull EnvVars envVars, @Nonnull EnvironmentExpander expander) {
        this.envVars = envVars;
        watchedVars = expander.getSensitiveVars();
    }

    public void scan(String text) {
        scanResults = watchedVars.stream().filter(e -> text.contains(envVars.get(e))).collect(Collectors.toList());
    }

    public void logResults(TaskListener listener) {
        if (scanResults != null && !scanResults.isEmpty()) {
            listener.getLogger().println("The following Groovy string may be insecure. Use single quotes to prevent leaking secrets via Groovy interpolation. Affected variables: "  + scanResults.toString());
            runReport.record(scanResults);
        }
    }

    private static final long serialVersionUID = 1L;
}
