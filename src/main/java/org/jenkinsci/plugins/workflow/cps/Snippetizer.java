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

import hudson.Extension;
import hudson.Functions;
import hudson.model.Action;
import hudson.model.Descriptor;
import hudson.model.DescriptorByNameOwner;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.RootAction;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.lang.model.SourceVersion;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Takes a {@link Step} as configured through the UI and tries to produce equivalent Groovy code.
 */
@Extension public class Snippetizer implements RootAction, DescriptorByNameOwner {
    
    static String object2Groovy(Object o) throws UnsupportedOperationException {
        Class<? extends Object> clazz = o.getClass();
        for (StepDescriptor d : StepDescriptor.all()) {
            if (d.clazz.equals(clazz)) {
                StringBuilder b = new StringBuilder(d.getFunctionName());
                Step step = (Step) o;
                Map<String,Object> args = new TreeMap<String,Object>(d.defineArguments(step));
                boolean first = true;
                boolean singleMap = args.size() == 1 && args.values().iterator().next() instanceof Map;
                for (Map.Entry<String,Object> entry : args.entrySet()) {
                    if (first) {
                        first = false;
                        if (d.takesImplicitBlockArgument() || singleMap) {
                            b.append('(');
                        } else {
                            b.append(' ');
                        }
                    } else {
                        b.append(", ");
                    }
                    String key = entry.getKey();
                    if (args.size() > 1 || !isDefaultKey(step, key)) {
                        b.append(key).append(": ");
                    }
                    render(b, entry.getValue());
                }
                if (d.takesImplicitBlockArgument()) {
                    if (!args.isEmpty()) {
                        b.append(')');
                    }
                    b.append(" {\n    // some block\n}");
                } else if (singleMap) {
                    b.append(')');
                } else if (args.isEmpty()) {
                    b.append("()");
                }
                return b.toString();
            }
        }
        throw new UnsupportedOperationException("Unknown step " + clazz);
    }

    private static boolean isDefaultKey(Step step, String key) {
        String[] names = new ClassDescriptor(step.getClass()).loadConstructorParamNames();
        return names.length == 1 && key.equals(names[0]);
    }

    private static void render(StringBuilder b, Object value) {
        if (value == null) {
            b.append("null");
            return;
        }
        Class<?> valueC = value.getClass();
        if (valueC == String.class || valueC == Character.class) {
            String text = String.valueOf(value);
            if (text.contains("\n")) {
                b.append("'''").append(text.replace("\\", "\\\\").replace("'", "\\'")).append("'''");
            } else {
                b.append('\'').append(text.replace("\\", "\\\\").replace("'", "\\'")).append('\'');
            }
        } else if (valueC == Boolean.class || valueC == Integer.class || valueC == Long.class) {
            b.append(value);
        } else if (value instanceof List) {
            List<?> list = (List<?>) value;
            b.append('[');
            boolean first = true;
            for (Object elt : list) {
                if (first) {
                    first = false;
                } else {
                    b.append(", ");
                }
                render(b, elt);
            }
            b.append(']');
        } else if (value instanceof Map) {
            Map<?,?> map = (Map) value;
            b.append('[');
            boolean first = true;
            for (Map.Entry<?,?> entry : map.entrySet()) {
                if (first) {
                    first = false;
                } else {
                    b.append(", ");
                }
                Object key = entry.getKey();
                if (key instanceof String && SourceVersion.isName((String) key)) {
                    b.append(key);
                } else {
                    render(b, key);
                }
                b.append(": ");
                render(b, entry.getValue());
            }
            b.append(']');
        } else {
            b.append("<object of type ").append(valueC.getCanonicalName()).append('>');
        }
    }

    public static final String ACTION_URL = "pipeline-groovy";

    @Override public String getUrlName() {
        return ACTION_URL;
    }

    @Override public String getIconFileName() {
        return null;
    }

    @Override public String getDisplayName() {
        // Do not want to add to main Jenkins sidepanel.
        return null;
    }

    @Override public Descriptor getDescriptorByName(String id) {
        return Jenkins.getActiveInstance().getDescriptorByName(id);
    }

    @Restricted(DoNotUse.class)
    public Collection<? extends StepDescriptor> getStepDescriptors(boolean advanced) {
        TreeSet<StepDescriptor> t = new TreeSet<StepDescriptor>(new StepDescriptorComparator());
        for (StepDescriptor d : StepDescriptor.all()) {
            if (d.isAdvanced() == advanced) {
                t.add(d);
            }
        }
        return t;
    }

    @Restricted(DoNotUse.class) // for stapler
    public Iterable<GlobalVariable> getGlobalVariables() {
        // TODO order TBD. Alphabetical? Extension.ordinal?
        return GlobalVariable.ALL;
    }

    @Restricted(NoExternalUse.class)
    public static final String GENERATE_URL = ACTION_URL + "/generateSnippet";

    @Restricted(DoNotUse.class) // accessed via REST API
    public HttpResponse doGenerateSnippet(StaplerRequest req, @QueryParameter String json) throws Exception {
        // TODO is there not an easier way to do this? Maybe Descriptor.newInstancesFromHeteroList on a one-element JSONArray?
        JSONObject jsonO = JSONObject.fromObject(json);
        Jenkins j = Jenkins.getActiveInstance();
        Class<?> c = j.getPluginManager().uberClassLoader.loadClass(jsonO.getString("stapler-class"));
        StepDescriptor descriptor = (StepDescriptor) j.getDescriptor(c.asSubclass(Step.class));
        Object o;
        try {
            o = descriptor.newInstance(req, jsonO);
        } catch (RuntimeException x) { // e.g. IllegalArgumentException
            return HttpResponses.plainText(Functions.printThrowable(x));
        }
        try {
            String groovy = object2Groovy(o);
            if (descriptor.isAdvanced()) {
                String warning = Messages.Snippetizer_this_step_should_not_normally_be_used_in();
                groovy = "// " + warning + "\n" + groovy;
            }
            return HttpResponses.plainText(groovy);
        } catch (UnsupportedOperationException x) {
            Logger.getLogger(CpsFlowExecution.class.getName()).log(Level.WARNING, "failed to render " + json, x);
            return HttpResponses.plainText(x.getMessage());
        }
    }

    private static class StepDescriptorComparator implements Comparator<StepDescriptor>, Serializable {
        @Override
        public int compare(StepDescriptor o1, StepDescriptor o2) {
            return o1.getFunctionName().compareTo(o2.getFunctionName());
        }
        private static final long serialVersionUID = 1L;
    }

    @Restricted(DoNotUse.class)
    @Extension public static class PerJobAdder extends TransientActionFactory<Job> {

        @Override public Class<Job> type() {
            return Job.class;
        }

        @Override public Collection<? extends Action> createFor(Job target) {
            // TODO probably want an API for FlowExecutionContainer or something
            if (target.getClass().getName().equals("org.jenkinsci.plugins.workflow.job.WorkflowJob") && target.hasPermission(Item.EXTENDED_READ)) {
                return Collections.singleton(new LocalAction());
            } else {
                return Collections.emptySet();
            }
        }

    }

    /**
     * May be added to various contexts to offer the Pipeline Groovy link where it is appropriate.
     * To use, define a {@link TransientActionFactory} of some kind of {@link Item}.
     * If the target {@link Item#hasPermission} {@link Item#EXTENDED_READ},
     * return one {@link LocalAction}. Otherwise return an empty set.
     */
    public static class LocalAction extends Snippetizer {

        @Override public String getDisplayName() {
            return "Pipeline Groovy";
        }

        public String getIconClassName() {
            return "icon-help";
        }

    }

}
