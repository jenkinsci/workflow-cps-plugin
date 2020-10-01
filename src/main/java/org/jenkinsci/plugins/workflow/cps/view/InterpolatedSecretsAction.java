package org.jenkinsci.plugins.workflow.cps.view;

import hudson.model.Run;
import jenkins.model.RunAction2;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Action to generate the UI report for watched environment variables
 */
@Restricted(NoExternalUse.class)
public class InterpolatedSecretsAction implements RunAction2 {

    private List<InterpolatedWarnings> interpolatedWarnings;
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

    public void record(@Nonnull String stepName, @Nonnull Map<String, Object> stepArguments, @Nonnull List<String> interpolatedVariables) {
        if (interpolatedWarnings == null) {
            interpolatedWarnings = new ArrayList<>();
        }
        interpolatedWarnings.add(new InterpolatedWarnings(stepName, stepArguments, interpolatedVariables));
    }

    public List<String> getWarningsOutput() {
        List<String> outputList = new ArrayList<>();
        StringBuilder sb = new StringBuilder();

        for (InterpolatedWarnings warning : interpolatedWarnings) {
            sb.append(warning.stepName + "(");
            for (Map.Entry<String, Object> argEntry : warning.stepArguments.entrySet()) {
                sb.append(argEntry.getKey() + ": " + argEntry.getValue().toString());
            }
            sb.append(")\n");
            sb.append("interpolated variable(s): " + warning.interpolatedVariables.toString());
        }
        outputList.add(sb.toString());
        return outputList;
    }

    public List<InterpolatedWarnings> getWarnings() {
       return interpolatedWarnings;
    }

    public boolean getHasWarnings() {
        if (interpolatedWarnings == null || interpolatedWarnings.isEmpty()) {
            return false;
        } else {
            return true;
        }
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

    @ExportedBean
    static class InterpolatedWarnings implements Serializable {
        final String stepName;
        final Map<String, Object> stepArguments;
        final List<String> interpolatedVariables;

        private InterpolatedWarnings(@Nonnull String stepName, @Nonnull Map<String, Object> stepArguments, @Nonnull List<String> interpolatedVariables) {
            this.stepName = stepName;
            this.stepArguments = stepArguments;
            this.interpolatedVariables = interpolatedVariables;
        }

        @Exported
        public String getStepSignature() {
            StringBuilder sb = new StringBuilder();
            sb.append(stepName + "(");
            for (Map.Entry<String, Object> argEntry : stepArguments.entrySet()) {
                sb.append(argEntry.getKey() + ": " + argEntry.getValue().toString());
            }
            sb.append(")");
            return sb.toString();
        }

        @Override
        public String toString() {
            return getStepSignature();
        }
    }
}
