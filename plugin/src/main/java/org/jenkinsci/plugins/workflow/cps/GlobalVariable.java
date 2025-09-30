/*
 * The MIT License
 *
 * Copyright 2015 Jesse Glick.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import groovy.lang.GroovyObject;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.model.Job;
import hudson.model.Run;
import hudson.util.Iterators.FlattenIterator;
import java.util.Iterator;
import jenkins.model.RunAction2;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.steps.Step;

/**
 * Defines a provider of a global variable offered to flows.
 * Within a given flow execution, the variable is assumed to be a singleton.
 * It is created on demand if the script refers to it by name.
 * <p><strong>Note:</strong>
 * This extension point was designed for a handful of variables defined by generic Pipeline plugins.
 * If you find yourself implementing this in a domain-specific plugin,
 * you are probably doing too much work and harming usability in the process.
 * For example, there will be no {@link Snippetizer} integration except to list its name and help text.
 * Variables will also not generally be compatible with Declarative Pipeline,
 * and may not work at all in alternate {@link FlowExecution} implementations.
 * It is also all too easy to introduce security vulnerabilities for variables implemented using Groovy code.
 * Just because you can does not mean you should.
 * If at all possible, limit your plugin to implementing plain {@link Step}s.
 * <p>
 * Should have a view named {@code help} offering usage.
 *
 * @see GlobalVariableSet
 */
public abstract class GlobalVariable implements ExtensionPoint {

    /**
     * Defines the name of the variable.
     * @return a Java identifier
     */
    public abstract @NonNull String getName();

    /**
     * Gets or creates the singleton value of the variable.
     * If the object is stateful, and the state should not be managed externally (such as with a {@link RunAction2}),
     * then the implementation is responsible for saving it in the {@link CpsScript#getBinding}.
     * @param script the script we are running
     * @return a POJO or {@link GroovyObject}
     * @throws Exception if there was any problem creating it (will be thrown up to the script)
     * @see CpsScript#getProperty
     */
    public abstract @NonNull Object getValue(@NonNull CpsScript script) throws Exception;

    /**
     * @deprecated use {@link #forRun} instead
     */
    @Deprecated
    public static final Iterable<GlobalVariable> ALL = forRun(null);

    /**
     * Returns all the registered {@link GlobalVariable}s for some context.
     * @param run see {@link GlobalVariableSet#forRun}
     * @return a possibly empty list
     */
    public static @NonNull Iterable<GlobalVariable> forRun(@CheckForNull final Run<?, ?> run) {
        return new Iterable<>() {
            @Override
            public Iterator<GlobalVariable> iterator() {
                return new FlattenIterator<>(
                        ExtensionList.lookup(GlobalVariableSet.class).iterator()) {
                    @Override
                    protected Iterator<GlobalVariable> expand(GlobalVariableSet vs) {
                        return vs.forRun(run).iterator();
                    }
                };
            }
        };
    }

    /**
     * Returns all the registered {@link GlobalVariable}s for some context.
     * @param job see {@link GlobalVariableSet#forJob}
     * @return a possibly empty list
     */
    public static @NonNull Iterable<GlobalVariable> forJob(@CheckForNull final Job<?, ?> job) {
        return new Iterable<>() {
            @Override
            public Iterator<GlobalVariable> iterator() {
                return new FlattenIterator<>(
                        ExtensionList.lookup(GlobalVariableSet.class).iterator()) {
                    @Override
                    protected Iterator<GlobalVariable> expand(GlobalVariableSet vs) {
                        return vs.forJob(job).iterator();
                    }
                };
            }
        };
    }

    /**
     * Finds a particular variable by name.
     * @param name see {@link #getName}
     * @param run see {@link GlobalVariableSet#forRun}
     * @return the first matching variable, or null if there is none
     */
    public static @CheckForNull GlobalVariable byName(@NonNull String name, @CheckForNull Run<?, ?> run) {
        for (GlobalVariable var : forRun(run)) {
            if (var.getName().equals(name)) {
                return var;
            }
        }
        return null;
    }
}
