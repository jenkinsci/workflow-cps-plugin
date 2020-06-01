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
import hudson.ExtensionList;
import hudson.Functions;
import hudson.model.Action;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.DescriptorByNameOwner;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.RootAction;
import hudson.tasks.BuildStepDescriptor;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.lang.model.SourceVersion;
import jenkins.model.Jenkins;
import jenkins.model.TransientActionFactory;
import jenkins.tasks.SimpleBuildStep;
import net.sf.json.JSONObject;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.jenkinsci.plugins.structs.describable.HeterogeneousObjectType;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.DoNotUse;
import org.kohsuke.accmod.restrictions.NoExternalUse;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.HttpResponses;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest;

/**
 * Takes a {@link Step} as configured through the UI and tries to produce equivalent Groovy code.
 */
@Extension public class Snippetizer implements RootAction, DescriptorByNameOwner {

    /**
     * Short-hand for the top-level invocation.
     */
    static String step2Groovy(Step s) throws UnsupportedOperationException {
        return object2Groovy(new StringBuilder(), s, false).toString();
    }

    /**
     * Publicly accessible version of {@link #object2Groovy(StringBuilder, Object, boolean)} that translates an object into
     * the equivalent Pipeline Groovy string.
     *
     * @param o The object to translate.
     * @return A string translation of the object.
     */
    public static String object2Groovy(Object o) throws UnsupportedOperationException {
        return object2Groovy(new StringBuilder(), o, false).toString();
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

        if (clazz == Boolean.class || clazz == Integer.class || clazz == Long.class ||
                clazz == Float.class || clazz == Double.class ||
                clazz == Byte.class || clazz == Short.class) {
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
                boolean blockArgument = d.takesImplicitBlockArgument();

                if (d.isMetaStep()) {
                    // if we have a symbol name for the wrapped Describable, we can produce
                    // a more concise form that hides it
                    DescribableModel<?> m = DescribableModel.of(d.clazz);
                    DescribableParameter p = m.getFirstRequiredParameter();
                    if (p!=null) {
                        Object wrapped = uninst.getArguments().get(p.getName());
                        if (wrapped instanceof UninstantiatedDescribable) {
                            // if we cannot represent this 'o' in a concise syntax that hides meta-step, set this to true
                            boolean failSimplification = false;

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

                            if (!canUseMetaStep(nested))
                                failSimplification = true;

                            if (!failSimplification) {
                                // write out in a short-form
                                UninstantiatedDescribable combined = new UninstantiatedDescribable(
                                        nested.getSymbol(), nested.getKlass(), copy);
                                combined.setModel(nested.getModel());

                                return ud2groovy(b, combined, blockArgument, nestedExp);
                            }
                        }
                    } else {
                        // this can only happen with buggy meta-step
                        LOGGER.log(Level.WARNING, "Buggy meta-step "+d.clazz+" defines no mandatory parameter");
                        // use the default code path to write it out as: metaStep(describable(...))
                    }
                }

                uninst.setSymbol(d.getFunctionName());
                return functionCall(b, uninst, blockArgument, nestedExp);
            }
        }

        // unknown type
        return b.append("<object of type ").append(clazz.getCanonicalName()).append('>');
    }

    /**
     * Can this symbol name be used to produce a short hand?
     */
    private static boolean canUseMetaStep(UninstantiatedDescribable ud) {
        return canUseSymbol(ud) && StepDescriptor.metaStepsOf(ud.getSymbol()).size()==1;
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
        if (!canUseSymbol(ud)) {
            // if there's no symbol, we need to write this as [$class:...]
            return map2groovy(b, ud.toShallowMap());
        }

        return functionCall(b, ud, blockArgument, nested);
    }

    private static boolean canUseSymbol(UninstantiatedDescribable ud) {
        if (ud.getSymbol() == null) {
            // if there's no symbol, we need to write this as [$class:...]
            return false;
        }

        if (StepDescriptor.byFunctionName(ud.getSymbol()) != null) {
            // if the symbol collides with existing step name, then we cannot use it
            return false;
        }

        return true;
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
        final boolean needParenthesis = (blockArgument ^ args.isEmpty()) || isSingleMap(args) || isSingleList(args) || nested;

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
            return !canUseSymbol((UninstantiatedDescribable)v);
        }
        return false;
    }

    /**
     * If the single argument is a list, it must be wrapped in parentheses.
     *
     * @param args
     *     Argument map
     * @return
     *     True if there's only one argument and it's a list, false otherwise.
     */
    private static boolean isSingleList(Map<String, ?> args) {
        if (args.size()!=1) return false;
        Object v = args.values().iterator().next();
        return v instanceof List;
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
        return Jenkins.get().getDescriptorByName(id);
    }

    @Restricted(NoExternalUse.class)
    public Collection<QuasiDescriptor> getQuasiDescriptors(boolean advanced) {
        TreeSet<QuasiDescriptor> t = new TreeSet<>();
        for (StepDescriptor d : StepDescriptor.all()) {
            if (d.isAdvanced() == advanced) {
                t.add(new QuasiDescriptor(d));
                if (d.isMetaStep()) {
                    DescribableModel<?> m = DescribableModel.of(d.clazz);
                    Collection<DescribableParameter> parameters = m.getParameters();
                    if (parameters.size() == 1) {
                        DescribableParameter delegate = parameters.iterator().next();
                        if (delegate.isRequired()) {
                            if (delegate.getType() instanceof HeterogeneousObjectType) {
                                // TODO HeterogeneousObjectType does not yet expose symbol information, and DescribableModel.symbolOf is private
                                for (DescribableModel<?> delegateOptionSchema : ((HeterogeneousObjectType) delegate.getType()).getTypes().values()) {
                                    Class<?> delegateOptionType = delegateOptionSchema.getType();
                                    Descriptor<?> delegateDescriptor = Jenkins.get().getDescriptorOrDie(delegateOptionType.asSubclass(Describable.class));
                                    Set<String> symbols = SymbolLookup.getSymbolValue(delegateDescriptor);
                                    if (!symbols.isEmpty()) {
                                        t.add(new QuasiDescriptor(delegateDescriptor));
                                    }
                                }
                            }
                        }
                    } // TODO currently not handling metasteps with other parameters, either required or (like GenericSCMStep) not
                }
            }
        }
        return t;
    }

    /**
     * Represents a step or other step-like objects that should appear in {@link Snippetizer}’s main dropdown list
     * and can generate some fragment of Pipeline script.
     * {@link #real} can be a {@link StepDescriptor}, in which case we generate an invocation of that step.
     * Or it can be any {@link Descriptor} that can be run by a {@linkplain StepDescriptor#isMetaStep meta step},
     * such as a {@link BuildStepDescriptor} of a {@link SimpleBuildStep} (from {@code CoreStep}) with a {@link Symbol},
     * because from the user point of view a regular {@link Describable} run via a metastep
     * is syntactically indistinguishable from a true {@link Step}.
     */
    @Restricted(NoExternalUse.class)
    public static final class QuasiDescriptor implements Comparable<QuasiDescriptor> {

        public final Descriptor<?> real;

        QuasiDescriptor(Descriptor<?> real) {
            this.real = real;
        }

        public String getSymbol() {
            if (real instanceof StepDescriptor) {
                return ((StepDescriptor) real).getFunctionName();
            } else {
                Set<String> symbolValues = SymbolLookup.getSymbolValue(real);
                if (!symbolValues.isEmpty()) {
                    return symbolValues.iterator().next();
                } else {
                    throw new AssertionError("Symbol present but no values defined.");
                }
            }
        }

        @Override public int compareTo(QuasiDescriptor o) {
            return getSymbol().compareTo(o.getSymbol());
        }

        @Override public boolean equals(Object obj) {
            return obj instanceof QuasiDescriptor && real == ((QuasiDescriptor) obj).real;
        }

        @Override public int hashCode() {
            return real.hashCode();
        }

        @Override public String toString() {
            return getSymbol() + "=" + real.clazz.getSimpleName();
        }

    }

    @Restricted(DoNotUse.class) // for stapler
    public Iterable<GlobalVariable> getGlobalVariables() {
        // TODO order TBD. Alphabetical? Extension.ordinal?
        StaplerRequest req = Stapler.getCurrentRequest();
        return GlobalVariable.forJob(req != null ? req.findAncestorObject(Job.class) : null);
    }

    @Restricted(NoExternalUse.class)
    public static final String GENERATE_URL = ACTION_URL + "/generateSnippet";

    @Restricted(DoNotUse.class) // accessed via REST API
    public HttpResponse doGenerateSnippet(StaplerRequest req, @QueryParameter String json) throws Exception {
        // TODO is there not an easier way to do this? Maybe Descriptor.newInstancesFromHeteroList on a one-element JSONArray?
        JSONObject jsonO = JSONObject.fromObject(json);
        Jenkins j = Jenkins.get();
        Class<?> c = j.getPluginManager().uberClassLoader.loadClass(jsonO.getString("stapler-class"));
        Descriptor descriptor = j.getDescriptor(c.asSubclass(Describable.class));
        if (descriptor == null) {
            return HttpResponses.text("<could not find " + c.getName() + ">");
        }
        Object o;
        try {
            o = descriptor.newInstance(req, jsonO);
        } catch (RuntimeException x) { // e.g. IllegalArgumentException
            return HttpResponses.text(Functions.printThrowable(x));
        }
        try {
            Step step = null;
            if (o instanceof Step) {
                step = (Step) o;
            } else {
                // Look for a metastep which could take this as its delegate.
                for (StepDescriptor d : StepDescriptor.allMeta()) {
                    if (d.getMetaStepArgumentType().isInstance(o)) {
                        DescribableModel<?> m = DescribableModel.of(d.clazz);
                        DescribableParameter soleRequiredParameter = m.getSoleRequiredParameter();
                        if (soleRequiredParameter != null) {
                            step = d.newInstance(Collections.singletonMap(soleRequiredParameter.getName(), o));
                            break;
                        }
                    }
                }
            }
            if (step == null) {
                return HttpResponses.text("Cannot find a step corresponding to " + o.getClass().getName());
            }
            String groovy = step2Groovy(step);
            if (descriptor instanceof StepDescriptor && ((StepDescriptor) descriptor).isAdvanced()) {
                String warning = Messages.Snippetizer_this_step_should_not_normally_be_used_in();
                groovy = "// " + warning + "\n" + groovy;
            }
            return HttpResponses.text(groovy);
        } catch (UnsupportedOperationException x) {
            Logger.getLogger(CpsFlowExecution.class.getName()).log(Level.WARNING, "failed to render " + json, x);
            return HttpResponses.text(x.getMessage());
        }
    }

    @Restricted(DoNotUse.class) // for stapler
    public @CheckForNull Item getItem(StaplerRequest req) {
         return req.findAncestorObject(Item.class);
    }

    /**
     * Used to generate the list of links on the sidepanel.
     */
    @Nonnull
    public List<SnippetizerLink> getSnippetizerLinks() {
        return ExtensionList.lookup(SnippetizerLink.class);
    }

    @Restricted(NoExternalUse.class)
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
            return Messages.Pipeline_Syntax();
        }

        public String getIconClassName() {
            return "icon-help";
        }

    }

    private static final Logger LOGGER = Logger.getLogger(Snippetizer.class.getName());
}
