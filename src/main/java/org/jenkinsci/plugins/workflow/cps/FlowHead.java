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

package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Outcome;
import com.google.common.annotations.VisibleForTesting;
import hudson.model.Action;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.actions.FlowNodeAction;
import org.jenkinsci.plugins.workflow.actions.TimingAction;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.PROGRAM;

import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;

import javax.annotation.Nonnull;

/**
 * Growing tip of the node graph.
 *
 * <h2>Persistence</h2>
 * <p>
 * This object is a part of two independent serialization.
 * One is when this gets serialized as a part of {@link CpsFlowExecution} through XStream, for example
 * as a part of the workflow build object. We store the flow head ID and flow node ID in that case.
 *
 * <p>
 * The other is when this gets serialized as a part of the program state of {@link CpsThreadGroup},
 * through River. In this case we just store the flow head ID, and upon deserialization we resolve
 * back to one of the {@link CpsFlowExecution#heads} constant.
 *
 * @author Kohsuke Kawaguchi
 */
@PersistIn(PROGRAM)
final class FlowHead implements Serializable {
    private /*almost final except for serialization*/ int id;
    private /*almost final except for serialization*/ transient CpsFlowExecution execution;

    @VisibleForTesting
    FlowNode head; // TODO: rename to node

    FlowHead(CpsFlowExecution execution, int id) {
        this.id = id;
        this.execution = execution;
    }

    /**
     * Assigns a unique ID.
     */
    FlowHead(CpsFlowExecution execution) {
        this(execution, execution.iota());
    }

    /**
     * Creates a new {@link FlowHead} that points to the same node.
     */
    public FlowHead fork() {
        FlowHead h = new FlowHead(execution);
        h.head = this.head;
        execution.addHead(h);
        return h;
    }

    public int getId() {
        return id;
    }

    public CpsFlowExecution getExecution() {
        return execution;
    }

    void newStartNode(FlowStartNode n) throws IOException {
        if (execution.flowStartNodeActions != null) {
            for (Action a : execution.flowStartNodeActions) {
                if (a instanceof FlowNodeAction) {
                    ((FlowNodeAction) a).onLoad(n);
                }
                n.addAction(a);
            }
            execution.flowStartNodeActions.clear();
        } // may be unset from loadProgramFailed
        synchronized (execution) {
            this.head = execution.startNodes.push(n);
        }
        execution.storage.storeNode(head, false);

        CpsThreadGroup c = CpsThreadGroup.current();
        if (c !=null) {
            // if the manipulation is from within the program executing thread, then
            // defer the notification till we get to a safe point.
            c.notifyNewHead(head);
        } else {
            // in recovering from error and such situation, we sometimes need to grow the graph
            // without running the program.
            // TODO can CpsThreadGroup.notifyNewHead be used instead to notify both kinds of listeners?
            execution.notifyListeners(Collections.singletonList(head), true);
            execution.notifyListeners(Collections.singletonList(head), false);
        }
    }

    /** Could be better described as "append to Flow graph" except for parallel cases. */
    void setNewHead(@Nonnull FlowNode v) {
        if (v == null) {
            // Because Findbugs isn't 100% at catching cases where this can happen and we really need to fail hard-and-fast
            throw new IllegalArgumentException("FlowHead.setNewHead called on FlowHead id="+this.id+" with a null FlowNode, execution="+this.execution);
        }
        try {
            if (this.head != null) {
                CpsFlowExecution.maybeAutoPersistNode(head);
                assert execution.storage.getNode(this.head.getId()) != null;
            }
            execution.storage.storeNode(v, true);
            assert execution.storage.getNode(v.getId()) != null;
            v.addAction(new TimingAction());
            CpsFlowExecution.maybeAutoPersistNode(v); // Persist node before changing head, otherwise Program can have unpersisted nodes and will fail to deserialize
            // NOTE: we may also need to persist the FlowExecution by persisting its owner (which will be a WorkflowRun)
            // But this will be handled by the WorkflowRun GraphListener momentarily
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to record new head or persist old: " + v, e);
        }
        this.head = v;
        CpsThreadGroup c = CpsThreadGroup.current();
        if (c !=null) {
            // if the manipulation is from within the program executing thread, then
            // defer the notification till we get to a safe point.
            c.notifyNewHead(v);
        } else {
            // in recovering from error and such situation, we sometimes need to grow the graph
            // without running the program.
            // TODO can CpsThreadGroup.notifyNewHead be used instead to notify both kinds of listeners?
            execution.notifyListeners(Collections.singletonList(v), true);
            execution.notifyListeners(Collections.singletonList(v), false);
        }
    }

    FlowNode get() {
        return this.head;
    }

    /**
     * If the given outcome is a failure, mark the current head as a failure.
     */
    public void markIfFail(Outcome o) {
        // record the failure in a step
        if (o.isFailure()) {
            get().addAction(new ErrorAction(o.getAbnormal()));
        }
    }

    // used only during deserialization
    void setForDeserialize(FlowNode v) {
        this.head = v;
    }

    void remove() {
        getExecution().removeHead(this);
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        oos.writeInt(id);
    }

    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        id = ois.readInt();
        // we'll replace this with one of execution.heads()
    }

    @Nonnull
    private Object readResolve() {
        execution = CpsFlowExecution.PROGRAM_STATE_SERIALIZATION.get();
        if (execution!=null) {
            // See if parallel loading?
            FlowHead myHead = execution.getFlowHead(id);
            if (myHead == null) {
                throw new IllegalStateException("FlowHead loading problem at deserialize: Null FlowHead with id "+id+" in execution "+execution);
            }
            return myHead;
        } else {
            LOGGER.log(Level.WARNING, "Tried to load a FlowHead from program with no Execution in PROGRAM_STATE_SERIALIZATION");
            return this;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(FlowHead.class.getName());
    private static final long serialVersionUID = 1L;

    @Override
    public String toString() {
        return id+":"+head;
    }
}
