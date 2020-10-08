package org.jenkinsci.plugins.workflow.cps.view;

import hudson.model.Run;
import jenkins.model.RunAction2;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Action to generate the UI report for watched environment variables
 */
@Restricted(NoExternalUse.class)
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

    public void record(@Nonnull String stepName, @Nonnull List<String> interpolatedVariables, @Nonnull String nodeId) {
        interpolatedWarnings.add(new InterpolatedWarnings(stepName, interpolatedVariables, run, nodeId));
    }

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
        final Run run;
        final String nodeId;

        private InterpolatedWarnings(@Nonnull String stepName, @Nonnull List<String> interpolatedVariables, @Nonnull Run run, @Nonnull String nodeId) {
            this.stepName = stepName;
            this.interpolatedVariables = interpolatedVariables;
            this.run = run;
            this.nodeId = nodeId;
        }

        @Exported
        public String getStepSignature() {
            StringBuilder sb = new StringBuilder();
            String failReason = null;
            if (run instanceof FlowExecutionOwner.Executable) {
                try {
                    FlowExecutionOwner owner = ((FlowExecutionOwner.Executable) run).asFlowExecutionOwner();
                    if (owner != null) {
                        FlowNode node = owner.get().getNode(nodeId);
                        if (node != null) {
                            ArgumentsAction argumentsAction = node.getPersistentAction(ArgumentsAction.class);
                            if (argumentsAction != null) {
                                Map<String, Object> stepArguments = argumentsAction.getArguments();
                                sb.append(stepName + "(");
                                for (Map.Entry<String, Object> argEntry : stepArguments.entrySet()) {
                                    Object value = argEntry.getValue();
                                    String valueString = String.valueOf(value);
                                    if (value instanceof ArgumentsAction.NotStoredReason) {
                                        switch ((ArgumentsAction.NotStoredReason) value) {
                                            case OVERSIZE_VALUE:
                                                valueString = "argument omitted due to length";
                                                break;
                                            case UNSERIALIZABLE:
                                                valueString = "unable to serialize argument";
                                                break;
                                            default:
                                                break;
                                        }
                                    }
                                    sb.append(argEntry.getKey() + ": " + valueString);
                                }
                                sb.append(")");
                            } else {
                                failReason = "null arguments action";
                            }
                        } else {
                            failReason = "null flow node";
                        }
                    } else {
                        failReason = "null flow execution owner";
                    }
                } catch (IOException e) {
                    failReason = "could not get flow node";
                }
            } else {
                failReason = "not an instance of FlowExecutionOwner.Executable";
            }

            if (failReason != null) {
                return "Unable to construct " +  stepName;
            } else {
                return sb.toString();
            }
        }

        @Exported
        public List<String> getInterpolatedVariables() {
            return interpolatedVariables;
        }
    }
}
