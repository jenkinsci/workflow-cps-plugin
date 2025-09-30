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

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.PROGRAM;

import com.google.common.util.concurrent.ListenableFuture;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.ExtensionList;
import hudson.model.Result;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import net.jcip.annotations.Immutable;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyInvoker;
import org.jenkinsci.plugins.workflow.steps.DynamicContext;
import org.jenkinsci.plugins.workflow.support.DefaultStepContext;

/**
 * Holds a set of contextual objects as per {@link BodyInvoker#withContext} in a given scope.
 * Also interprets {@link DynamicContext}.
 */
@Immutable
@PersistIn(PROGRAM)
final class ContextVariableSet implements Serializable {

    private static final Logger LOGGER = Logger.getLogger(ContextVariableSet.class.getName());

    private final ContextVariableSet parent;
    private final List<Object> values = new ArrayList<>();

    ContextVariableSet(ContextVariableSet parent) {
        this.parent = parent;
    }

    private static final ThreadLocal<Set<DynamicContextQuery>> dynamicContextClasses =
            ThreadLocal.withInitial(HashSet::new);

    private static final class DynamicContextQuery {
        final DynamicContext dynamicContext;
        final Class<?> key;

        DynamicContextQuery(DynamicContext dynamicContext, Class<?> key) {
            this.dynamicContext = dynamicContext;
            this.key = key;
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof DynamicContextQuery
                    && dynamicContext == ((DynamicContextQuery) obj).dynamicContext
                    && key == ((DynamicContextQuery) obj).key;
        }

        @Override
        public int hashCode() {
            return dynamicContext.hashCode() ^ key.hashCode();
        }
    }

    @FunctionalInterface
    interface ThrowingSupplier<T> {
        T get() throws IOException;
    }

    <T> T get(Class<T> key, ThrowingSupplier<FlowExecution> execution, ThrowingSupplier<FlowNode> node)
            throws IOException, InterruptedException {
        for (Object v : values) {
            if (key.isInstance(v)) {
                LOGGER.fine(() -> "found a " + v.getClass().getName() + " in " + this);
                return key.cast(v);
            }
        }
        class Delegate extends DefaultStepContext implements DynamicContext.DelegatedContext {
            @Override
            protected <T> T doGet(Class<T> key) throws IOException, InterruptedException {
                return ContextVariableSet.this.get(key, execution, node);
            }

            @Override
            protected FlowExecution getExecution() throws IOException {
                return execution.get();
            }

            @Override
            protected FlowNode getNode() throws IOException {
                return node.get();
            }

            @Override
            public void onSuccess(Object result) {
                throw new AssertionError();
            }

            @Override
            public boolean isReady() {
                throw new AssertionError();
            }

            @Override
            public ListenableFuture<Void> saveState() {
                throw new AssertionError();
            }

            @Override
            public void setResult(Result r) {
                throw new AssertionError();
            }

            @Override
            public BodyInvoker newBodyInvoker() throws IllegalStateException {
                throw new AssertionError();
            }

            @SuppressFBWarnings(value = "EQ_UNUSUAL", justification = "DefaultStepContext does not delegate to this")
            @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
            @Override
            public boolean equals(Object o) {
                throw new AssertionError();
            }

            @Override
            public int hashCode() {
                throw new AssertionError();
            }

            @Override
            public void onFailure(Throwable t) {
                throw new AssertionError();
            }
        }
        DynamicContext.DelegatedContext delegate = new Delegate();
        for (DynamicContext dynamicContext : ExtensionList.lookup(DynamicContext.class)) {
            DynamicContextQuery query = new DynamicContextQuery(dynamicContext, key);
            Set<DynamicContextQuery> dynamicStack = dynamicContextClasses.get();
            if (dynamicStack.add(query)) { // thus, being newly added to the stack
                try {
                    T v = dynamicContext.get(key, delegate);
                    if (v != null) {
                        LOGGER.fine(() ->
                                "looked up a " + v.getClass().getName() + " from " + dynamicContext + " in " + this);
                        return v;
                    }
                } finally {
                    dynamicStack.remove(query);
                }
            }
        }
        if (parent != null) {
            return parent.get(key, execution, node);
        } else {
            return null;
        }
    }

    @Override
    public String toString() {
        return "ContextVariableSet"
                + values.stream().map(Object::getClass).map(Class::getName).collect(Collectors.toList())
                + (parent != null ? "<" + parent : "");
    }

    /**
     * Obtains {@link ContextVariableSet} that inherits from the given parent and adds the specified overrides.
     */
    public static ContextVariableSet from(ContextVariableSet parent, List<Object> overrides) {
        if (overrides == null || overrides.isEmpty()) return parent; // nothing to override

        ContextVariableSet o = new ContextVariableSet(parent);
        o.values.addAll(overrides);
        return o;
    }

    private static final long serialVersionUID = 1L;
}
