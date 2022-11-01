package org.jenkinsci.plugins.workflow.cps.nodes;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

/**
 * Optional interface for {@link FlowNode} that has associated {@link StepDescriptor}.
 * Retained for binary and API compatibility but the interface has been pushed up a level into the
 *  {@link org.jenkinsci.plugins.workflow.graph.StepNode} interface in workflow-api plugin to allow broader access.
 */
@Deprecated
@SuppressFBWarnings(value="NM_SAME_SIMPLE_NAME_AS_INTERFACE", justification="We want to keep the SimpleName the same, to make it easy to replace usages")
public interface StepNode extends org.jenkinsci.plugins.workflow.graph.StepNode {
}
