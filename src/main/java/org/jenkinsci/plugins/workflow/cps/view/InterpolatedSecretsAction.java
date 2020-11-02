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

import hudson.model.Run;
import jenkins.model.RunAction2;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
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
import java.util.stream.Collectors;

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

    public void record(@Nonnull String stepName, @Nonnull List<String> interpolatedVariables, @Nonnull String nodeId) {
        interpolatedWarnings.add(new InterpolatedWarnings(stepName, interpolatedVariables, run, nodeId));
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
    public static class InterpolatedWarnings implements RunAction2 {
        final String stepName;
        final List<String> interpolatedVariables;
        final String nodeId;
        private transient Run run;

        InterpolatedWarnings(@Nonnull String stepName, @Nonnull List<String> interpolatedVariables, @Nonnull Run run, @Nonnull String nodeId) {
            this.stepName = stepName;
            this.interpolatedVariables = interpolatedVariables;
            this.run = run;
            this.nodeId = nodeId;
        }

        @Exported
        public String getStepSignature() {
            Map<String, Object> stepArguments;
            try {
                stepArguments = getStepArguments(run, nodeId);
            } catch (IllegalStateException e) {
                return "Unable to construct " +  stepName + ": " + e.getMessage();
            }

            return stepArguments.entrySet().stream()
                    .map(InterpolatedSecretsAction::argumentToString)
                    .collect(Collectors.joining(", ", stepName + "(", ")"));
        }

        @Nonnull
        private Map<String, Object> getStepArguments(Run run, String nodeId) throws IllegalStateException {
            String failReason;
            if (run instanceof FlowExecutionOwner.Executable) {
                try {
                    FlowExecutionOwner owner = ((FlowExecutionOwner.Executable) run).asFlowExecutionOwner();
                    if (owner != null) {
                        FlowNode node = owner.get().getNode(nodeId);
                        if (node != null) {
                            ArgumentsAction argumentsAction = node.getPersistentAction(ArgumentsAction.class);
                            if (argumentsAction != null) {
                                return argumentsAction.getArguments();
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
            throw new IllegalStateException(failReason);
        }

        @Exported
        public List<String> getInterpolatedVariables() {
            return interpolatedVariables;
        }

        @Override
        public void onAttached(Run<?, ?> run) {
            this.run = run;
        }

        @Override
        public void onLoad(Run<?, ?> run) {
            this.run = run;
        }

        @Override
        public String getIconFileName() {
            return null;
        }

        @Override
        public String getDisplayName() {
            return null;
        }

        @Override
        public String getUrlName() {
            return null;
        }
    }

    private static String argumentToString(Object arg) {
        String valueString;
        if (arg instanceof Map.Entry) {
            Map.Entry argEntry = (Map.Entry<String, Object>) arg;
            Object value = argEntry.getValue();
            if (value instanceof ArgumentsAction.NotStoredReason) {
                switch ((ArgumentsAction.NotStoredReason) value) {
                    case OVERSIZE_VALUE:
                        valueString = "argument omitted due to length";
                        break;
                    case UNSERIALIZABLE:
                        valueString = "unable to serialize argument";
                        break;
                    default:
                        valueString = String.valueOf(value);
                        break;
                }
            } else if (value instanceof Map || value  instanceof List || value instanceof UninstantiatedDescribable) {
                valueString = argumentToString(value);
            } else {
                valueString = String.valueOf(value);
            }
            return argEntry.getKey() + ": " + valueString;
        } else if (arg instanceof Map) {
            valueString = ((Map<?, ?>) arg).entrySet().stream()
                    .map(InterpolatedSecretsAction::argumentToString)
                    .collect(Collectors.joining(", ", "[", "]"));
        } else if (arg instanceof List) {
            valueString = ((List<?>) arg).stream()
                    .map(InterpolatedSecretsAction::argumentToString)
                    .collect(Collectors.joining(", ", "[", "]"));
        } else if (arg instanceof UninstantiatedDescribable) {
            UninstantiatedDescribable ud = (UninstantiatedDescribable) arg;
            String udName = null;
            if (ud.getSymbol() != null) {
                udName = '@' + ud.getSymbol();
            }
            if (ud.getKlass() != null) {
                udName = '$' + ud.getKlass() + udName;
            }

            Map<String, ?> udArgs = ud.getArguments();
            valueString = udArgs.entrySet().stream()
                    .map(InterpolatedSecretsAction::argumentToString)
                    .collect(Collectors.joining(", ", udName + "(", ")"));
        } else {
            valueString = String.valueOf(arg);
        }

        return valueString;
    }

    private static String mapToString(Map<String, Object> valueMap) {
        return valueMap.entrySet().stream()
                .map(InterpolatedSecretsAction::argumentToString)
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
