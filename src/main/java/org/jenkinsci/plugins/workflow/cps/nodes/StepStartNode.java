package org.jenkinsci.plugins.workflow.cps.nodes;

import hudson.model.Action;
import org.jenkinsci.plugins.workflow.actions.BodyInvocationAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import java.io.ObjectStreamException;
import java.util.Collections;

/**
 * {@link BlockStartNode} for executing {@link Step} with body closure.
 *
 * @author Kohsuke Kawaguchi
 */
public class StepStartNode extends BlockStartNode implements StepNode {
    private String descriptorId;

    private transient StepDescriptor descriptor;

    public StepStartNode(CpsFlowExecution exec, StepDescriptor d, FlowNode parent) {
        super(exec, exec.iotaStr(), parent);
        this.descriptor = d;
        this.descriptorId = d!=null ? d.getId().intern() : null;

        // we use SimpleXStreamFlowNodeStorage, which uses XStream, so
        // constructor call is always for brand-new FlowNode that has not existed anywhere.
        // such nodes always have empty actions
        setActions(Collections.<Action>emptyList());
    }

    public StepDescriptor getDescriptor() {
        if (descriptor == null && descriptorId != null) {
            descriptor = StepDescriptorCache.getPublicCache().getDescriptor(descriptorId);
        }
        return descriptor;
    }

    protected Object readResolve() throws ObjectStreamException {
        if (descriptorId != null) {
            this.descriptorId = this.descriptorId.intern();
        }
        return super.readResolve();
    }

    @Override
    protected String getTypeDisplayName() {
        return getStepName() + (isBody() ?" : Body":"") + " : Start";
    }

    @Override
    protected String getTypeFunctionName() {
        if (isBody()) {
            return "{";
        } else {
            String fn = StepAtomNode.effectiveFunctionName(this);
            return fn != null ? fn : descriptorId;
        }
    }

    public boolean isBody() {
        return getAction(BodyInvocationAction.class)!=null;
    }

    public String getStepName() {
        String n = StepAtomNode.effectiveDisplayName(this);
        return n != null ? n : descriptorId;
    }
}
