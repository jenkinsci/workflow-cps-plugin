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
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.lang.model.SourceVersion;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import net.sf.json.JSONObject;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Takes a {@link Step} as configured through the UI and tries to produce equivalent Groovy code.
 */
@Extension public class Snippetizer implements RootAction, DescriptorByNameOwner {

    /**
     * Short-hand for the top-level invocation.
     */
    static String object2Groovy(Object o) throws UnsupportedOperationException {
        return object2Groovy(new StringBuilder(),o, false).toString();
    }

    /**
     * Renders the invocation syntax to re-create a given object 'o' into 'b'
     *
     * @param nestedExp
     *      true if this object is written as a nested expression (in which case we always produce parenthesis for readability)
     * @return  the same object as 'b'
     */
    static StringBuilder object2Groovy(StringBuilder b, Object o, boolean nestedExp) throws UnsupportedOperationException {
        if (o == null) {
            return b.append("null");
        }
        final Class<?> clazz = o.getClass();

        if (clazz == String.class || clazz == Character.class) {
            String text = String.valueOf(o);
            if (text.contains("\n")) {
                b.append("'''").append(text.replace("\\", "\\\\").replace("'", "\\'")).append("'''");
            } else {
                b.append('\'').append(text.replace("\\", "\\\\").replace("'", "\\'")).append('\'');
            }
            return b;
        }

        if (clazz == Boolean.class || clazz == Integer.class || clazz == Long.class) {
            return b.append(o);
        }

        if (o instanceof List) {
            return list2groovy(b, (List<?>) o);
        }

        if (o instanceof Map) {
            return map2groovy(b, (Map) o);
        }

        if (o instanceof UninstantiatedDescribable) {
            return ud2groovy(b,(UninstantiatedDescribable)o, false, nestedExp);
        }

        for (StepDescriptor d : StepDescriptor.all()) {
            if (d.clazz.equals(clazz)) {
                Step step = (Step) o;
                UninstantiatedDescribable uninst = d.uninstantiate(step);

                if (d.isMetaStep()) {
                    // if we cannot represent this 'o' in a concise syntax that hides meta-step, set this to true
                    boolean failSimplification = false;

                    // if we have a symbol name for the wrapped Describable, we can produce
                    // a more concise form that hides it
                    DescribableModel<?> m = new DescribableModel(d.clazz);
                    DescribableParameter p = m.getFirstRequiredParameter();
                    if (p!=null) {
                        Object wrapped = uninst.getArguments().get(p.getName());
                        if (wrapped instanceof UninstantiatedDescribable) {
                            UninstantiatedDescribable nested = (UninstantiatedDescribable) wrapped;
                            TreeMap<String, Object> copy = new TreeMap<>(nested.getArguments());
                            for (Entry<String, ?> e : uninst.getArguments().entrySet()) {
                                if (!e.getKey().equals(p.getName())) {
                                    if (copy.put(e.getKey(), e.getValue()) != null) {
                                        // collision between a parameter in meta-step and wrapped-step,
                                        // which cannot be reconciled unless we explicitly write out
                                        // meta-step
                                        failSimplification = true;
                                    }
                                }
                            }

                            if (nested.getSymbol() == null) {
                                // no symbol name on the nested object means there's no short name
                                failSimplification = true;
                            } else
                            if (StepDescriptor.byFunctionName(nested.getSymbol())!=null) {
                                // there's a step that has the same name. DSL.invokeMethod prefers step over describable
                                // so this needs to be written out as a literal map
                                failSimplification = true;
                            }

                            if (!failSimplification) {
                                // write out in a short-form
                                UninstantiatedDescribable combined = new UninstantiatedDescribable(
                                        nested.getSymbol(), nested.getKlass(), copy);
                                combined.setModel(nested.getModel());

                                return ud2groovy(b, combined, false, nestedExp);
                            }
                        }
                    } else {
                        // this can only happen with buggy meta-step
                        LOGGER.log(Level.WARNING, "Buggy meta-step "+d.clazz+" defines no mandatory parameter");
                        // use the default code path to write it out as: metaStep(describable(...))
                    }
                }

                uninst.setSymbol(d.getFunctionName());
                return functionCall(b, uninst, d.takesImplicitBlockArgument(), nestedExp);
            }
        }

        // unknown type
        return b.append("<object of type ").append(clazz.getCanonicalName()).append('>');
    }

    private static StringBuilder list2groovy(StringBuilder b, List<?> o) {
        b.append('[');
        boolean first = true;
        for (Object elt : o) {
            if (first) {
                first = false;
            } else {
                b.append(", ");
            }
            object2Groovy(b, elt, true);
        }
        return b.append(']');
    }

    private static StringBuilder map2groovy(StringBuilder b, Map<?,?> map) {
        b.append('[');
        mapWithoutBracket2groovy(b, map);
        return b.append(']');
    }

    private static void mapWithoutBracket2groovy(StringBuilder b, Map<?, ?> map) {
        boolean first = true;
        for (Entry<?, ?> entry : map.entrySet()) {
            if (first) {
                first = false;
            } else {
                b.append(", ");
            }
            Object key = entry.getKey();
            if (key instanceof String && SourceVersion.isName((String) key)) {
                b.append(key);
            } else {
                object2Groovy(b, key, true);
            }
            b.append(": ");
            object2Groovy(b, entry.getValue(), true);
        }
    }

    /**
     * Writes out a snippet that instantiates {@link UninstantiatedDescribable}
     *
     * @param nested
     *      true if this object is written as a nested expression (in which case we always produce parenthesis for readability
     */
    private static StringBuilder ud2groovy(StringBuilder b, UninstantiatedDescribable ud, boolean blockArgument, boolean nested) {
        if (ud.getSymbol() == null) {
            // if there's no symbol, we need to write this as [$class:...]
            return map2groovy(b, ud.toShallowMap());
        }

        if (StepDescriptor.byFunctionName(ud.getSymbol()) != null) {
            // if the symbol collides with existing step name, then we cannot use it
            return map2groovy(b, ud.toShallowMap());
        }

        return functionCall(b, ud, blockArgument, nested);
    }

    /**
     * Writes out a given {@link UninstantiatedDescribable} as a function call form.
     *
     * @param nested
     *      true if this object is written as a nested expression (in which case we always produce parenthesis for readability
     */
    private static StringBuilder functionCall(StringBuilder b, UninstantiatedDescribable ud, boolean blockArgument, boolean nested) {
        Map<String, ?> args = ud.getArguments();

        // if the whole argument is just one map?

        // the call needs explicit parenthesis sometimes
        //   a block argument normally requires a () around arguments, and if arguments are empty you need explicit (),
        //   but not if both is the case!
        final boolean needParenthesis = (blockArgument ^ args.isEmpty()) || isSingleMap(args) || nested;

        b.append(ud.getSymbol());
        b.append(needParenthesis ? '(': ' ');

        if (ud.hasSoleRequiredArgument()) {
            // lone argument optimization, which gets rid of named arguments and just write one value, like
            // retry (5) { ... }
            object2Groovy(b, args.values().iterator().next(), true);
        } else {
            // usual form, which calls out argument names, like
            // git url:'...', browser:'...'
            mapWithoutBracket2groovy(b,args);
        }

        if (needParenthesis)
            b.append(')');

        if (blockArgument) {
            if (!args.isEmpty())    b.append(' ');
            b.append("{\n    // some block\n}");
        }

        return b;
    }

    /**
     * If the sole argument is a map, its [...] bracket cannot be present.
     *
     * Historically we've disambiguated this by adding (...) around the function call.
     * TODO: I claim removing both () and [] would be better.
     *
       % groovysh
       Groovy Shell (2.0.2, JVM: 1.7.0_07)
       Type 'help' or '\h' for help.
       ---------------------------------------------------------------------------------------------------------------------------------------------
       groovy:000> def foo(o) { println o }
       ===> true
       groovy:000> foo abc:1, def:2
       [abc:1, def:2]
       ===> null
       groovy:000> foo(abc:1, def:2)
       [abc:1, def:2]
       ===> null
       groovy:000> foo [abc:1,def:2]
       ERROR org.codehaus.groovy.control.MultipleCompilationErrorsException:
       startup failed:
       groovysh_evaluate: 2: No map entry allowed at this place
       . At [2:9]  @ line 2, column 9.
          foo [abc:1,def:2]
                  ^

       1 error

               at java_lang_Runnable$run.call (Unknown Source)
       groovy:000> foo([abc:1,def:2])
       [abc:1, def:2]
       ===> null
     */
    private static boolean isSingleMap(Map<String, ?> args) {
        if (args.size()!=1) return false;
        Object v = args.values().iterator().next();
        if (v instanceof Map)
            return true;
        if (v instanceof UninstantiatedDescribable) {
            // UninstantiatedDescribable can be written out as a Map so treat it as a map
            return ((UninstantiatedDescribable)v).getSymbol()==null;
        }
        return false;
    }

    public static final String ACTION_URL = "pipeline-syntax";

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

    @Restricted(DoNotUse.class) // for stapler
    public @CheckForNull Item getItem(StaplerRequest req) {
         return req.findAncestorObject(Item.class);
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
            return "Pipeline Syntax";
        }

        public String getIconClassName() {
            return "icon-help";
        }

    }

    private static final Logger LOGGER = Logger.getLogger(Snippetizer.class.getName());
}
