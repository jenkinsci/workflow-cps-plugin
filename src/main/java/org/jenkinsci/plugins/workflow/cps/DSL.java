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

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;
import groovy.lang.Closure;
import groovy.lang.GString;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.GroovyRuntimeException;
import hudson.EnvVars;
import hudson.model.Computer;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.TaskListener;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;
import org.apache.commons.lang.StringUtils;
import org.codehaus.groovy.reflection.CachedClass;
import org.codehaus.groovy.reflection.ReflectionCache;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.structs.SymbolLookup;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import static org.jenkinsci.plugins.workflow.cps.ThreadTaskResult.*;
import org.jenkinsci.plugins.workflow.cps.actions.ArgumentsActionImpl;
import org.jenkinsci.plugins.workflow.cps.nodes.StepAtomNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepEndNode;
import org.jenkinsci.plugins.workflow.cps.nodes.StepStartNode;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;
import org.jenkinsci.plugins.workflow.cps.steps.LoadStep;
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.BodyExecutionCallback;
import org.jenkinsci.plugins.workflow.steps.MissingContextVariableException;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jvnet.hudson.annotation_indexer.Index;
import org.kohsuke.stapler.ClassDescriptor;
import org.kohsuke.stapler.NoStaplerConstructorException;

/**
 * Calls {@link Step}s and other DSL objects.
 */
@PersistIn(PROGRAM)
public class DSL extends GroovyObjectSupport implements Serializable {
    private final FlowExecutionOwner handle;
    private transient CpsFlowExecution exec;
    private transient Map<String,StepDescriptor> functions;

    private static final Logger LOGGER = Logger.getLogger(DSL.class.getName());

    public DSL(FlowExecutionOwner handle) {
        this.handle = handle;
    }

    protected Object readResolve() throws IOException {
        return this;
    }

    private static final String KEEP_STEP_ARGUMENTS_PROPERTYNAME = (DSL.class.getName()+".keepStepArguments");

    private static boolean isKeepStepArguments = StringUtils.isEmpty(System.getProperty(KEEP_STEP_ARGUMENTS_PROPERTYNAME))
            || Boolean.parseBoolean(System.getProperty(KEEP_STEP_ARGUMENTS_PROPERTYNAME));

    /** Tell us if we should store {@link Step} arguments in an {@link org.jenkinsci.plugins.workflow.actions.ArgumentsAction}
     *  or simply discard them (if set to false, explicitly) */
    public static boolean isKeepStepArguments() {
        return isKeepStepArguments;
    }

    /**
     * Executes the {@link Step} implementation specified by the name argument.
     *
     * @return
     *      If the step completes execution synchronously, the result will be
     *      returned. Otherwise this method {@linkplain Continuable#suspend(Object) suspends}.
     */
    @Override
    @CpsVmThreadOnly
    public Object invokeMethod(String name, Object args) {
        try {
            if (exec==null)
                exec = (CpsFlowExecution) handle.get();
        } catch (IOException e) {
            throw new GroovyRuntimeException(e);
        }

        if (functions == null) {
            functions = new TreeMap<>();
            while (StepDescriptor.all().isEmpty()) {
                LOGGER.warning("Jenkins does not seem to be fully started yet, waiting…");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException x) {
                    throw new GroovyRuntimeException(x);
                }
            }
            for (StepDescriptor d : StepDescriptor.all()) {
                functions.put(d.getFunctionName(), d);
            }
        }
        final StepDescriptor sd = functions.get(name);
        if (sd != null) {
            return invokeStep(sd,args);
        }
        if (SymbolLookup.get().findDescriptor(Describable.class, name) != null) {
            return invokeDescribable(name,args);
        }

        Set<String> symbols = new TreeSet<>();
        Set<String> globals = new TreeSet<>();
        // TODO SymbolLookup only lets us find a particular symbol, not enumerate them
        try {
            for (Class<?> e : Index.list(Symbol.class, Jenkins.getActiveInstance().pluginManager.uberClassLoader, Class.class)) {
                if (Descriptor.class.isAssignableFrom(e)) {
                    symbols.addAll(SymbolLookup.getSymbolValue(e));
                }
            }
            Queue.Executable executable = exec.getOwner().getExecutable();
            for (GlobalVariable var : GlobalVariable.forRun(executable instanceof Run ? (Run) executable : null)) {
                globals.add(var.getName());
            }
        } catch (IOException x) {
            Logger.getLogger(DSL.class.getName()).log(Level.WARNING, null, x);
        }
        // TODO probably this should be throwing a subtype of groovy.lang.MissingMethodException
        throw new NoSuchMethodError("No such DSL method '" + name + "' found among steps " + functions.keySet() + " or symbols " + symbols + " or globals " + globals);
    }

    /**
     * When {@link #invokeMethod(String, Object)} is calling a {@link StepDescriptor}
     */
    protected Object invokeStep(StepDescriptor d, Object args) {
        final NamedArgsAndClosure ps = parseArgs(args, d);

        CpsThread thread = CpsThread.current();

        FlowNode an;

        // TODO: generalize the notion of Step taking over the FlowNode creation.
        boolean hack = d instanceof ParallelStep.DescriptorImpl || d instanceof LoadStep.DescriptorImpl;

        if (ps.body == null && !hack) {
            an = new StepAtomNode(exec, d, thread.head.get());
            // TODO: use CPS call stack to obtain the current call site source location. See JENKINS-23013
            thread.head.setNewHead(an);
        } else {
            an = new StepStartNode(exec, d, thread.head.get());
            thread.head.setNewHead(an);
        }

        final CpsStepContext context = new CpsStepContext(d,thread,handle,an,ps.body);
        Step s;
        boolean sync;
        ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        try {
            d.checkContextAvailability(context);
            Thread.currentThread().setContextClassLoader(CpsVmExecutorService.ORIGINAL_CONTEXT_CLASS_LOADER.get());
            s = d.newInstance(ps.namedArgs);
            try {
                // No point storing empty arguments, and ParallelStep is a special case where we can't store its closure arguments
                if (ps.namedArgs != null && !(ps.namedArgs.isEmpty()) && isKeepStepArguments() && !(s instanceof ParallelStep)) {
                    // Get the environment variables to find ones that might be credentials bindings
                    Computer comp = context.get(Computer.class);
                    EnvVars allEnv = new EnvVars(context.get(EnvVars.class));
                    if (comp != null && allEnv != null) {
                        allEnv.entrySet().removeAll(comp.getEnvironment().entrySet());
                    }
                    an.addAction(new ArgumentsActionImpl(ps.namedArgs, allEnv));
                }
            } catch (Exception e) {
                // Avoid breaking execution because we can't store some sort of crazy Step argument
                LOGGER.log(Level.WARNING, "Error storing the arguments for step: "+d.getFunctionName(), e);
            }

            StepExecution e = s.start(context);
            thread.setStep(e);
            sync = e.start();
        } catch (Exception e) {
            if (e instanceof MissingContextVariableException)
                reportMissingContextVariableException(context, (MissingContextVariableException)e);
            context.onFailure(e);
            s = null;
            sync = true;
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }

        if (sync) {
            assert context.bodyInvokers.isEmpty() : "If a step claims synchronous completion, it shouldn't invoke body";

            if (context.getOutcome()==null) {
                context.onFailure(new AssertionError("Step "+s+" claimed to have ended synchronously, but didn't set the result via StepContext.onSuccess/onFailure"));
            }

            thread.setStep(null);

            // if the execution has finished synchronously inside the start method
            // we just move on accordingly
            if (an instanceof StepStartNode) {
                // no body invoked, so EndNode follows StartNode immediately.
                thread.head.setNewHead(new StepEndNode(exec, (StepStartNode)an, an));
            }

            thread.head.markIfFail(context.getOutcome());

            return context.replay();
        } else {
            // if it's in progress, suspend it until we get invoked later.
            // when it resumes, the CPS caller behaves as if this method returned with the resume value
            Continuable.suspend(new ThreadTaskImpl(context));

            // the above method throws an exception to unwind the call stack, and
            // the control then goes back to CpsFlowExecution.runNextChunk
            // so the execution will never reach here.
            throw new AssertionError();
        }
    }

    private static String loadSoleArgumentKey(StepDescriptor d) {
        try {
            String[] names = new ClassDescriptor(d.clazz).loadConstructorParamNames();
            return names.length == 1 ? names[0] : null;
        } catch (NoStaplerConstructorException e) {
            return null;
        }
    }

    /**
     * When {@link #invokeMethod(String, Object)} is calling a generic {@link Descriptor}
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    protected Object invokeDescribable(String symbol, Object _args) {
        List<StepDescriptor> metaSteps = StepDescriptor.metaStepsOf(symbol);
        StepDescriptor metaStep = metaSteps.size()==1 ? metaSteps.get(0) : null;

        boolean singleArgumentOnly = false;
        if (metaStep != null) {
            Descriptor symbolDescriptor = SymbolLookup.get().findDescriptor((Class)(metaStep.getMetaStepArgumentType()), symbol);
            DescribableModel<?> symbolModel = new DescribableModel(symbolDescriptor.clazz);

            singleArgumentOnly = symbolModel.hasSingleRequiredParameter() && symbolModel.getParameters().size() == 1;
        }

        // The only time a closure is valid is when the resulting Describable is immediately executed via a meta-step
        NamedArgsAndClosure args = parseArgs(_args, metaStep!=null && metaStep.takesImplicitBlockArgument(),
                UninstantiatedDescribable.ANONYMOUS_KEY, singleArgumentOnly);
        UninstantiatedDescribable ud = new UninstantiatedDescribable(symbol, null, args.namedArgs);

        if (metaStep==null) {
            // there's no meta-step associated with it, so this symbol is not executable.
            // in this case we assume this is building a nested object used as an eventual
            // parameter of an executable symbol, e.g.,
            //
            // hg source: 'https://whatever/', clean: true, browser: kallithea('https://whatever/')

            // also note that in this case 'd' is not trustworthy, as depending on
            // where this UninstantiatedDescribable is ultimately used, the symbol
            // might be resolved with a specific type.
            return ud;
        } else {
            Descriptor d = SymbolLookup.get().findDescriptor((Class)(metaStep.getMetaStepArgumentType()), symbol);
            try {
                // execute this Describable through a meta-step

                // split args between MetaStep (represented by mm) and Describable (represented by dm)
                DescribableModel<?> mm = new DescribableModel(metaStep.clazz);
                DescribableModel<?> dm = new DescribableModel(d.clazz);
                DescribableParameter p = mm.getFirstRequiredParameter();
                if (p==null) {
                    // meta-step not having a required parameter is a bug in this meta step
                    throw new IllegalArgumentException("Attempted to use meta-step "+metaStep.getFunctionName()+" to process "+symbol+" but this meta-step is buggy; it has no mandatory parameter");
                }

                // order of preference:
                //      1. mandatory parameter in mm
                //      2. mandatory parameter in dm
                //      3. other parameters in mm
                //      4. other parameters in dm
                // mm is preferred over dm because that way at least the arguments that mm defines
                // act consistently
                Map<String,Object> margs = new TreeMap<>();
                Map<String,Object> dargs = new TreeMap<>();
                for (Entry<String, ?> e : ud.getArguments().entrySet()) {
                    String n = e.getKey();
                    Object v = e.getValue();
                    DescribableParameter mp = mm.getParameter(n);
                    DescribableParameter dp = dm.getParameter(n);

                    if (mp!=null && mp.isRequired()) {
                        margs.put(n,v);
                    } else
                    if (dp!=null && dp.isRequired()) {
                        dargs.put(n,v);
                    } else
                    if (mp!=null) {
                        margs.put(n,v);
                    } else {
                        // dp might be null, but this error will be caught by UD.instantiate() later
                        dargs.put(n,v);
                    }
                }

                ud = new UninstantiatedDescribable(symbol, null, dargs);
                margs.put(p.getName(),ud);

                return invokeStep(metaStep,new NamedArgsAndClosure(margs,args.body));
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to prepare "+symbol+" step",e);
            }
        }
    }

    /**
     * Reports a user-friendly error message for {@link MissingContextVariableException}.
     */
    private void reportMissingContextVariableException(CpsStepContext context, MissingContextVariableException e) {
        TaskListener tl;
        try {
            tl = context.get(TaskListener.class);
            if (tl==null)       return; // if we can't report an error, give up
        } catch (IOException _) {
            return;
        } catch (InterruptedException _) {
            return;
        }

        StringBuilder names = new StringBuilder();
        for (StepDescriptor p : e.getProviders()) {
            if (names.length()>0)   names.append(',');
            names.append(p.getFunctionName());
        }

        PrintStream logger = tl.getLogger();
        logger.println(e.getMessage());
        if (names.length()>0)
            logger.println("Perhaps you forgot to surround the code with a step that provides this, such as: "+names);
    }

    static class NamedArgsAndClosure {
        final Map<String,Object> namedArgs;
        final Closure body;

        private NamedArgsAndClosure(Map<?,?> namedArgs, Closure body) {
            this.namedArgs = new LinkedHashMap<String,Object>();
            this.body = body;

            for (Map.Entry<?,?> entry : namedArgs.entrySet()) {
                String k = entry.getKey().toString(); // coerces GString and more
                Object v = flattenGString(entry.getValue());
                this.namedArgs.put(k, v);
            }
        }
    }

    /**
     * Coerce {@link GString}, to save {@link StepDescriptor#newInstance(Map)} from being made aware of that.
     * This is not the only type coercion that Groovy does, so this is not very kosher, but
     * doing a proper coercion like Groovy does require us to know the type that the receiver
     * expects.
     * For reference, Groovy does {@linkplain ReflectionCache#getCachedClass ReflectionCache.getCachedClass(types[i]).}{@linkplain CachedClass#coerceArgument coerceArgument(a)}.
     * Note that {@link DescribableModel#instantiate} would also handle {@link GString} in {@code coerce},
     * but better to do it here in the Groovy-specific code so we do not need to rely on that.
     * @return {@code v} or an equivalent with all {@link GString}s flattened, including in nested {@link List}s or {@link Map}s
     */
    private static Object flattenGString(Object v) {
        if (v instanceof GString) {
            return v.toString();
        } else if (v instanceof List) {
            boolean mutated = false;
            List<Object> r = new ArrayList<>();
            for (Object o : ((List<?>) v)) {
                Object o2 = flattenGString(o);
                mutated |= o != o2;
                r.add(o2);
            }
            return mutated ? r : v;
        } else if (v instanceof Map) {
            boolean mutated = false;
            Map<Object,Object> r = new LinkedHashMap<>();
            for (Map.Entry<?,?> e : ((Map<?, ?>) v).entrySet()) {
                Object k = e.getKey();
                Object k2 = flattenGString(k);
                Object o = e.getValue();
                Object o2 = flattenGString(o);
                mutated |= k != k2 || o != o2;
                r.put(k2, o2);
            }
            return mutated ? r : v;
        } else {
            return v;
        }
    }

    static NamedArgsAndClosure parseArgs(Object arg, StepDescriptor d) {
        boolean singleArgumentOnly = false;
        try {
            DescribableModel<?> stepModel = new DescribableModel<>(d.clazz);
            singleArgumentOnly = stepModel.hasSingleRequiredParameter() && stepModel.getParameters().size() == 1;
        } catch (NoStaplerConstructorException e) {
            // Ignore steps without databound constructors and treat them as normal.
        }
        return parseArgs(arg,d.takesImplicitBlockArgument(), loadSoleArgumentKey(d), singleArgumentOnly);
    }

    /**
     * Given the Groovy style argument packing used in the sole object parameter of {@link GroovyObject#invokeMethod(String, Object)},
     * compute the named argument map and an optional closure that represents the body.
     *
     * <p>
     * Positional arguments are not allowed, unless it has a single argument, in which case
     * it is passed as an argument named "value", that is:
     *
     * <pre>
     * foo(x)  => foo(value:x)
     * </pre>
     *
     * <p>
     * This handling is designed after how Java defines literal syntax for {@link Annotation}.
     *
     * @param arg
     *      Argument object of {@link GroovyObject#invokeMethod(String, Object)}
     * @param expectsBlock
     *      If a closure is a valid possible argument. If false and we see a block, this method throws an exception.
     * @param soleArgumentKey
     *      If the context in which this method call happens allow implicit sole default argument, specify its name.
     *      If null, the call must be with names arguments.
     */
    static NamedArgsAndClosure parseArgs(Object arg, boolean expectsBlock, String soleArgumentKey, boolean singleRequiredArg) {
        if (arg instanceof NamedArgsAndClosure)
            return (NamedArgsAndClosure) arg;
        if (arg instanceof Map) // TODO is this clause actually used?
            return new NamedArgsAndClosure((Map) arg, null);
        if (arg instanceof Closure && expectsBlock)
            return new NamedArgsAndClosure(Collections.<String,Object>emptyMap(),(Closure)arg);

        if (arg instanceof Object[]) {// this is how Groovy appears to pack argument list into one Object for invokeMethod
            List a = Arrays.asList((Object[])arg);
            if (a.size()==0)
                return new NamedArgsAndClosure(Collections.<String,Object>emptyMap(),null);

            Closure c=null;

            Object last = a.get(a.size()-1);
            if (last instanceof Closure && expectsBlock) {
                c = (Closure)last;
                a = a.subList(0,a.size()-1);
            }

            if (a.size()==1 && a.get(0) instanceof Map && !((Map) a.get(0)).containsKey("$class")) {
                Map mapArg = (Map) a.get(0);
                if (!singleRequiredArg ||
                        (soleArgumentKey != null && mapArg.size() == 1 && mapArg.containsKey(soleArgumentKey))) {
                    // this is how Groovy passes in Map
                    return new NamedArgsAndClosure(mapArg, c);
                }
            }

            switch (a.size()) {
            case 0:
                return new NamedArgsAndClosure(Collections.<String,Object>emptyMap(),c);
            case 1:
                return new NamedArgsAndClosure(singleParam(soleArgumentKey, a.get(0)), c);
            default:
                throw new IllegalArgumentException("Expected named arguments but got "+a);
            }
        }

        return new NamedArgsAndClosure(singleParam(soleArgumentKey, arg), null);
    }
    private static Map<String,Object> singleParam(String soleArgumentKey, Object arg) {
        if (soleArgumentKey != null) {
            return Collections.singletonMap(soleArgumentKey, arg);
        } else {
            throw new IllegalArgumentException("Expected named arguments but got " + arg);
        }
    }

    /**
     * If the step starts executing asynchronously, this task
     * executes at the safe point to switch {@link CpsStepContext} into the async mode.
     */
    private static class ThreadTaskImpl extends ThreadTask implements Serializable {
        private final CpsStepContext context;

        public ThreadTaskImpl(CpsStepContext context) {
            this.context = context;
        }

        @Override
        protected ThreadTaskResult eval(CpsThread cur) {
            invokeBody(cur);

            if (!context.switchToAsyncMode()) {
                // we have a result now, so just keep executing
                // TODO: if this fails with an exception, we need ability to resume by throwing an exception
                return resumeWith(context.getOutcome());
            } else {
                // beyond this point, StepContext can receive a result at any time and
                // that would result in a call to scheduleNextChunk(). So we the call to
                // switchToAsyncMode to happen inside 'synchronized(lock)', so that
                // the 'executing' variable gets set to null before the scheduleNextChunk call starts going.

                return suspendWith(new Outcome(context,null));
            }
        }

        private void invokeBody(CpsThread cur) {
            // prepare enough heads for all the bodies
            // the first one can reuse the current thread, but other ones need to create new heads
            // we want to do this first before starting body so that the order of heads preserve
            // natural ordering.
            FlowHead[] heads = new FlowHead[context.bodyInvokers.size()];
            for (int i = 0; i < heads.length; i++) {
                heads[i] = i==0 ? cur.head : cur.head.fork();
            }

            int idx=0;
            for (CpsBodyInvoker b : context.bodyInvokers) {
                // don't collect the first head, which is what we borrowed from our parent.
                FlowHead h = heads[idx];
                b.launch(cur, h);
                context.bodyHeads.add(h.getId());
                idx++;
            }

            context.bodyInvokers.clear();
        }

        /**
         * When a new {@link CpsThread} that runs the body completes, record
         * its new head.
         *
         * @deprecated
         *      Unused as of 1.2. Left here for serialization compatibility.
         */
        private static class HeadCollector extends BodyExecutionCallback {
            private final CpsStepContext context;
            private final FlowHead head;

            public HeadCollector(CpsStepContext context, FlowHead head) {
                this.context = context;
                this.head = head;
            }

            private void onEnd() {
            }

            @Override
            public void onSuccess(StepContext context, Object result) {
                onEnd();
            }

            @Override
            public void onFailure(StepContext context, Throwable t) {
                onEnd();
            }
        }


        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
