package org.jenkinsci.plugins.workflow.cps;

import hudson.EnvVars;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;

import javax.annotation.CheckForNull;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class EnvironmentWatcher implements Serializable {
    private EnvVars envVars = null;
    private EnvironmentExpander expander = null;
    private Set<String> watchedVars;
    private List<String> scanResults;

    private static final Logger LOGGER = Logger.getLogger(EnvironmentWatcher.class.getName());

    public EnvironmentWatcher(CpsStepContext context) {
        try {
            envVars = context.get(EnvVars.class);
            expander = context.get(EnvironmentExpander.class);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Error storing the arguments for step: " + context.getStepDescriptor().getFunctionName(), e);
        }
        if (expander != null) {
            watchedVars = expander.getSensitiveVars();
        }
    }

    public void scan(String text) {
        // TODO: would EnvVars ever be null?
        if (watchedVars == null || envVars == null) {
            scanResults = null;
        } else {
            scanResults = watchedVars.stream().filter(e -> text.contains(envVars.get(e))).collect(Collectors.toList());
        }
    }

    public void logResults(TaskListener listener) {
        if (scanResults != null && !scanResults.isEmpty()) {
            PrintStream logger;
            if (listener != null) {
                logger = listener.getLogger();
            } else {
                logger = System.out;
            }
            logger.println("The following Groovy string may be insecure. Use single quotes to prevent leaking secrets via Groovy interpolation. Affected variables: "  + scanResults.toString());
        }
    }
}
