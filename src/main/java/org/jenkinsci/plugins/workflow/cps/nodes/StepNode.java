package org.jenkinsci.plugins.workflow.cps.nodes;

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import javax.annotation.CheckForNull;

/**
 * Optional interface for {@link FlowNode} that has associated {@link StepDescriptor}.
 * Retained for binary and API compatibility but the interface has been pushed up a level into the
 *  workflow-api plugin to allow broader access.
 */
@Deprecated
public interface StepNode extends org.jenkinsci.plugins.workflow.graph.StepNode {
}
