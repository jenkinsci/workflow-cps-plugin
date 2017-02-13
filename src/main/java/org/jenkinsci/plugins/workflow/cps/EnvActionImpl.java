/*
 * The MIT License
 *
 * Copyright 2014 Jesse Glick.
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

import groovy.lang.GroovyObjectSupport;
import hudson.EnvVars;
import hudson.Extension;
import hudson.model.EnvironmentContributor;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.LogTaskListener;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import jenkins.model.RunAction2;
import org.jenkinsci.plugins.workflow.flow.FlowCopier;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.steps.EnvironmentExpander;
import org.jenkinsci.plugins.workflow.support.actions.EnvironmentAction;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.stapler.export.Exported;
import org.kohsuke.stapler.export.ExportedBean;

@ExportedBean
public class EnvActionImpl extends GroovyObjectSupport implements EnvironmentAction.IncludingOverrides, Serializable, RunAction2 {

    private static final Logger LOGGER = Logger.getLogger(EnvActionImpl.class.getName());
    private static final long serialVersionUID = 1;

    private final Map<String,String> env;
    private transient Run<?,?> owner;

    private EnvActionImpl() {
        this.env = new TreeMap<String,String>();
    }

    @Override public EnvVars getEnvironment() throws IOException, InterruptedException {
        TaskListener listener;
        if (owner instanceof FlowExecutionOwner.Executable) {
            FlowExecutionOwner executionOwner = ((FlowExecutionOwner.Executable) owner).asFlowExecutionOwner();
            if (executionOwner != null) {
                listener = executionOwner.getListener();
            } else {
                listener = new LogTaskListener(LOGGER, Level.INFO);
            }
        } else {
            listener = new LogTaskListener(LOGGER, Level.INFO);
        }
        EnvVars e = owner.getEnvironment(listener);
        e.putAll(env);
        return e;
    }

    @Exported(name="environment")
    @Override public Map<String,String> getOverriddenEnvironment() {
        return Collections.unmodifiableMap(env);
    }

    @Override public String getProperty(String propertyName) {
        try {
            CpsThread t = CpsThread.current();
            return EnvironmentExpander.getEffectiveEnvironment(getEnvironment(), t.getContextVariable(EnvVars.class), t.getContextVariable(EnvironmentExpander.class)).get(propertyName);
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, null, x);
            return null;
        }
    }

    @Override public void setProperty(String propertyName, Object newValue) {
        env.put(propertyName, String.valueOf(newValue));
        try {
            owner.save();
        } catch (IOException x) {
            throw new RuntimeException(x);
        }
    }

    @Override public String getIconFileName() {
        return null;
    }

    @Override public String getDisplayName() {
        return null;
    }

    @Override public String getUrlName() {
        return null;
    }

    @Override public void onAttached(Run<?,?> r) {
        owner = r;
    }

    @Override public void onLoad(Run<?,?> r) {
        owner = r;
    }

    /**
     * Gets the singleton instance for a given build, creating it on demand.
     */
    public static @Nonnull EnvActionImpl forRun(@Nonnull Run<?,?> run) throws IOException {
        synchronized (run) {
            EnvActionImpl action = run.getAction(EnvActionImpl.class);
            if (action == null) {
                action = new EnvActionImpl();
                run.addAction(action);
                run.save();
            }
            return action;
        }
    }

    @Extension public static class Binder extends GlobalVariable {
        @Override public String getName() {
            return "env";
        }
        @Override public EnvActionImpl getValue(CpsScript script) throws Exception {
            Run<?,?> run = script.$build();
            if (run != null) {
                return EnvActionImpl.forRun(run);
            } else {
                throw new IllegalStateException("no associated build");
            }
        }
        @Restricted(DoNotUse.class)
        public Collection<EnvironmentContributor> getEnvironmentContributors() {
            return EnvironmentContributor.all();
        }
    }

    @Restricted(DoNotUse.class)
    @Extension public static class Copier extends FlowCopier.ByRun {

        @Override public void copy(Run<?,?> original, Run<?,?> copy, TaskListener listener) throws IOException, InterruptedException {
            EnvActionImpl orig = original.getAction(EnvActionImpl.class);
            if (orig != null) {
                EnvActionImpl nue = EnvActionImpl.forRun(copy);
                for (Map.Entry<String,String> entry : orig.getOverriddenEnvironment().entrySet()) {
                    nue.setProperty(entry.getKey(), entry.getValue());
                }
            }
        }

    }

}
