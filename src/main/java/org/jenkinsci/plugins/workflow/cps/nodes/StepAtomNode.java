/*
 * The MIT License
 *
 * Copyright (c) 2013-2014, CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps.nodes;

import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Descriptor;
import org.jenkinsci.plugins.workflow.cps.CpsFlowExecution;
import org.jenkinsci.plugins.workflow.graph.AtomNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import java.io.ObjectStreamException;
import java.util.Collections;
import java.util.Set;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;

/**
 * {@link AtomNode} for executing {@link Step} without body closure.
 *
 * @author Kohsuke Kawaguchi
 */
@SuppressWarnings("deprecation")
public class StepAtomNode extends AtomNode implements StepNode {

    private String descriptorId;

    // once we successfully convert descriptorId to a real instance, cache that
    private transient StepDescriptor descriptor;

    public StepAtomNode(CpsFlowExecution exec, StepDescriptor d, FlowNode parent) {
        super(exec, exec.iotaStr(), parent);
        this.descriptorId = d!=null ? d.getId().intern() : null;

        // we use SimpleXStreamFlowNodeStorage, which uses XStream, so
        // constructor call is always for brand-new FlowNode that has not existed anywhere.
        // such nodes always have empty actions
        setActions(Collections.<Action>emptyList());
    }

    @Override public StepDescriptor getDescriptor() {
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

    static @CheckForNull String effectiveDisplayName(@Nonnull org.jenkinsci.plugins.workflow.graph.StepNode node) {
        StepDescriptor d = node.getDescriptor();
        if (d == null) {
            return null;
        }
        Class<?> delegateType = getDelegateType((FlowNode) node, d);
        if (delegateType != null && Describable.class.isAssignableFrom(delegateType)) {
            Descriptor<?> descriptor = Jenkins.getInstance().getDescriptor(delegateType.asSubclass(Describable.class));
            if (descriptor != null) {
                return descriptor.getDisplayName();
            }
        }
        return d.getDisplayName();
    }

    @Override
    protected String getTypeDisplayName() {
        String n = effectiveDisplayName(this);
        return n != null ? n : descriptorId;
    }

    static @CheckForNull String effectiveFunctionName(@Nonnull org.jenkinsci.plugins.workflow.graph.StepNode node) {
        StepDescriptor d = node.getDescriptor();
        if (d == null) {
            return null;
        }
        Class<?> delegateType = getDelegateType((FlowNode) node, d);
        if (delegateType != null) {
            Set<String> symbols = SymbolLookup.getSymbolValue(delegateType);
            if (!symbols.isEmpty()) {
                return symbols.iterator().next();
            }
        }
        return d.getFunctionName();
    }

    @Override
    protected String getTypeFunctionName() {
        String fn = effectiveFunctionName(this);
        return fn != null ? fn : descriptorId;
    }

    /**
     * @return for example {@code JUnitResultArchiver} given {@code junit '…'}
     */
    private static @CheckForNull Class<?> getDelegateType(@Nonnull FlowNode node, @Nonnull StepDescriptor d) {
        if (d.isMetaStep()) {
            DescribableParameter p = new DescribableModel<>(d.clazz).getFirstRequiredParameter();
            if (p != null) {
                Object arg = ArgumentsAction.getResolvedArguments(node).get(p.getName());
                if (arg instanceof UninstantiatedDescribable) {
                    DescribableModel<?> delegateModel = ((UninstantiatedDescribable) arg).getModel();
                    if (delegateModel != null) {
                        return delegateModel.getType();
                    }
                }
            }
        }
        return null;
    }

}
