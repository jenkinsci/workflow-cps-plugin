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
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import hudson.ExtensionList;
import hudson.Functions;
import hudson.Main;
import hudson.Util;
import hudson.model.Result;
import jenkins.model.Jenkins;
import jenkins.util.Timer;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.support.pickles.serialization.RiverWriter;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.util.logging.Level.*;
import javax.annotation.CheckForNull;
import static org.jenkinsci.plugins.workflow.cps.CpsFlowExecution.*;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.*;
import org.jenkinsci.plugins.workflow.pickles.Pickle;
import org.jenkinsci.plugins.workflow.pickles.PickleFactory;
import org.jenkinsci.plugins.workflow.support.pickles.SingleTypedPickleFactory;
import org.jenkinsci.plugins.workflow.support.storage.FlowNodeStorage;

/**
 * List of {@link CpsThread}s that form a single {@link CpsFlowExecution}.
 *
 * <p>
 * To make checkpointing easy, only one {@link CpsThread} runs at any point in time.
 *
 * @author Kohsuke Kawaguchi
 */
@PersistIn(PROGRAM)
@SuppressFBWarnings("SE_BAD_FIELD") // bogus warning about closures
public final class CpsThreadGroup implements Serializable {
    /**
     * {@link CpsThreadGroup} always belong to the same {@link CpsFlowExecution}.
     *
     * {@link CpsFlowExecution} and {@link CpsThreadGroup} persist separately,
     * so this field is not persisted, but get fixed up in {@link #readResolve()}
     */
    private /*almost final*/ transient CpsFlowExecution execution;

    /**
     * All the member threads by their {@link CpsThread#id}.
     *
     * All mutation occurs only on the CPS VM thread. Read access through {@link CpsStepContext#doGet}
     * and iteration through {@link CpsThreadDump#from(CpsThreadGroup)} may occur on other threads
     * (e.g. non-blocking steps, thread dumps from the UI).
     */
    private final NavigableMap<Integer,CpsThread> threads = new ConcurrentSkipListMap<>();

    /**
     * Unique thread ID generator.
     */
    private int iota;

    /**
     * Ensures only one thread updates CPS VM state at any given time
     * by queueing such tasks in here.
     */
    transient ExecutorService runner;

    /** Set while {@link #runner} is doing something. */
    transient boolean busy;

    /**
     * True if the build was automatically paused because quiet mode is enabled.
     * Used to avoid printing more than one pause message or scheduling more than one resumption task per build.
     * Independent of {@link #paused}.
     */
    private transient AtomicBoolean pausedByQuietMode;

    /**
     * True if the execution suspension is requested.
     *
     * <p>
     * This doesn't necessarily mean the CPS VM has responded and suspended the execution.
     * For that you need to do {@code scheduleRun().get()}.
     *
     * <p>
     * This state is intended for a use by humans to put the state of workflow execution
     * on hold (for example while inspecting a suspicious state or to perform a maintenance
     * when a failure is predictable.)
     */
    private /*almost final*/ AtomicBoolean paused = new AtomicBoolean();

    /**
     * "Exported" closures that are referenced by live {@link CpsStepContext}s.
     */
    public final Map<Integer,Closure> closures = new HashMap<>();

    private final @CheckForNull List<Script> scripts = new ArrayList<>();

    CpsThreadGroup(CpsFlowExecution execution) {
        this.execution = execution;
        setupTransients();
    }

    public CpsFlowExecution getExecution() {
        return execution;
    }

    /** Track a script so that we can fix up its {@link Script#getBinding}s after deserialization. */
    void register(Script script) {
        if (scripts != null) {
            scripts.add(script);
        }
    }

    @SuppressWarnings("unchecked")
    private Object readResolve() {
        execution = CpsFlowExecution.PROGRAM_STATE_SERIALIZATION.get();
        setupTransients();
        assert execution!=null;
        if (/* compatibility: the field will be null in old programs */ scripts != null && !scripts.isEmpty()) {
            GroovyShell shell = execution.getShell();
            // Take the canonical bindings from the main script and relink that object with that of the shell and all other loaded scripts which kept the same bindings.
            shell.getContext().getVariables().putAll(scripts.get(0).getBinding().getVariables());
            for (Script script : scripts) {
                script.setBinding(shell.getContext());
            }
        }
        return this;
    }

    private void setupTransients() {
        runner = new CpsVmExecutorService(this);
        pausedByQuietMode = new AtomicBoolean();
        if (paused == null) { // earlier versions did not have this field.
            paused = new AtomicBoolean();
        }
    }

    @CpsVmThreadOnly
    public CpsThread addThread(Continuable program, FlowHead head, ContextVariableSet contextVariables) {
        assertVmThread();
        CpsThread t = new CpsThread(this, iota++, program, head, contextVariables);
        threads.put(t.id, t);
        return t;
    }

    /**
     * Ensures that the current thread is running from {@link CpsVmExecutorService}
     *
     * @see CpsVmThreadOnly
     */
    private void assertVmThread() {
        assert current()==this;
    }

    /**
     * Returns the thread with the specified id.
     *
     * Normally called from the CPS VM thread, but may be called from other threads via {@link CpsStepContext#doGet}.
     *
     * @return
     *      null if the thread has finished executing.
     */
    public CpsThread getThread(int id) {
        CpsThread thread = threads.get(id);
        if (thread == null && LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "no thread " + id + " among " + threads.keySet(), new IllegalStateException());
        }
        return thread;
    }

    /**
     * Returns an unmodifiable snapshot of all threads in the thread group.
     */
    public Iterable<CpsThread> getThreads() {
        return threads.values();
    }

    @CpsVmThreadOnly("root")
    public @Nonnull BodyReference export(@Nonnull Closure body) {
        assertVmThread();
        int id = iota++;
        closures.put(id, body);
        LOGGER.log(FINE, "exporting {0}", id);
        return new StaticBodyReference(id,body);
    }

    @CpsVmThreadOnly("root")
    public @Nonnull BodyReference export(@Nonnull final Script body) {
        register(body);
        return export(new Closure(null) {
            @Override
            public Object call() {
                return body.run();
            }
        });
    }

    @CpsVmThreadOnly("root")
    public void unexport(BodyReference ref) {
        assertVmThread();
        if (ref==null)      return;
        if (closures.remove(ref.id) != null) {
            LOGGER.log(FINE, "unexporting {0}", ref.id);
        } else {
            LOGGER.log(WARNING, "double unexport of {0}", ref.id);
        }
    }

    /**
     * Schedules the execution of all the runnable threads.
     *
     * @return
     *      {@link Future} object that represents when the CPS VM is executed.
     */
    public Future<?> scheduleRun() {
        final SettableFuture<Void> f = SettableFuture.create();
        try {
            runner.submit(new Callable<Void>() {
                @SuppressFBWarnings(value="RV_RETURN_VALUE_IGNORED_BAD_PRACTICE", justification="runner.submit() result")
                public Void call() throws Exception {
                    Jenkins j = Jenkins.getInstanceOrNull();
                    if (j != null && !j.isQuietingDown() && execution != null && pausedByQuietMode.compareAndSet(true, false)) {
                        try {
                            execution.getOwner().getListener().getLogger().println("Resuming (Shutdown was canceled)");
                        } catch (IOException e) {
                            LOGGER.log(Level.WARNING, null, e);
                        }
                    }
                    if (paused.get() || j == null || (execution != null && j.isQuietingDown())) {
                        if (j != null && j.isQuietingDown() && execution != null && pausedByQuietMode.compareAndSet(false, true)) {
                            try {
                                execution.getOwner().getListener().getLogger().println("Pausing (Preparing for shutdown)");
                            } catch (IOException e) {
                               LOGGER.log(Level.WARNING, null, e);
                            }
                            Timer.get().schedule(new Runnable() {
                                @Override
                                public void run() {
                                    if (j.isQuietingDown()) {
                                        Timer.get().schedule(this, Main.isUnitTest ? 1 : 10, TimeUnit.SECONDS);
                                    } else {
                                        scheduleRun();
                                    }
                                }
                            }, Main.isUnitTest ? 1 : 10, TimeUnit.SECONDS);
                        }
                        // by doing the pause check inside, we make sure that scheduleRun() returns a
                        // future that waits for any previously scheduled tasks to be completed.
                        saveProgramIfPossible(true);
                        f.set(null);
                        return null;
                    }

                    boolean stillRunnable = run();
                    try {
                        if (stillRunnable) {
                            // we can run more.
                            runner.submit(this);
                        } else {
                            // we ensure any tasks submitted during run() will complete before we declare us complete
                            // those include things like notifying listeners or updating various other states
                            // runner is a single-threaded queue, so running a no-op and waiting for its completion
                            // ensures that everything submitted in front of us has finished.
                            runner.submit(new Runnable() {
                                public void run() {
                                    if (threads.isEmpty()) {
                                        runner.shutdown();
                                    }
                                    // the original promise of scheduleRun() is now complete
                                    f.set(null);
                                }
                            });
                        }
                    } catch (RejectedExecutionException x) {
                        // Was shut down by a prior task?
                        f.setException(x);
                    }
                    return null;
                }
            });
        } catch (RejectedExecutionException x) {
            return Futures.immediateFuture(null);
        }

        return f;
    }

    /**
     * Pauses the execution.
     *
     * @return
     *      {@link Future} object that represents the actual suspension of the CPS VM.
     *      When the {@link #pause()} method is called, CPS VM might be still executing.
     */
    public Future<?> pause() {
        paused.set(true);
        // CPS VM might have a long queue in its task list, so to properly ensure
        // that the execution has actually suspended, call scheduleRun() excessively
        return scheduleRun();
    }

    /**
     * If the execution is {@link #isPaused()}, cancel the pause state.
     */
    public void unpause() {
        if (paused.getAndSet(false)) {
            // some threads might have became executable while we were pausing.
            scheduleRun();
        } else {
            LOGGER.warning("were not paused to begin with");
        }
    }

    /**
     * Returns true if pausing has been requested.
     */
    public boolean isPaused() {
        return paused.get();
    }

    /**
     * Run the CPS program a little bit.
     *
     * <p>
     * The amount of execution needs to be small enough so as not to hog CPS VM thread
     * In particular, time sensitive activities like the interruption wants to run on CPS VM thread.
     *
     * @return
     *      true if this program can still execute further. false if the program is suspended
     *      and requires some external event to become resumable again. The false return value
     *      is akin to a Unix thread waiting for I/O completion.
     */
    @CpsVmThreadOnly("root")
    private boolean run() {
        boolean changed = false;
        boolean ending = false;
        boolean stillRunnable = false;

        // TODO: maybe instead of running all the thread, run just one thread in round robin
        for (CpsThread t : threads.values().toArray(new CpsThread[threads.size()])) {
            if (t.isRunnable()) {
                Outcome o = t.runNextChunk();
                if (o.isFailure()) {
                    assert !t.isAlive();    // failed thread is non-resumable

                    // workflow produced an exception
                    Result result = Result.FAILURE;
                    Throwable error = o.getAbnormal();
                    if (error instanceof FlowInterruptedException) {
                        result = ((FlowInterruptedException) error).getResult();
                    }
                    execution.setResult(result);
                    FlowNode fn = t.head.get();
                    if (fn != null) {
                        t.head.get().addAction(new ErrorAction(error));
                    }
                }

                if (!t.isAlive()) {
                    LOGGER.fine("completed " + t);
                    t.fireCompletionHandlers(o); // do this after ErrorAction is set above

                    threads.remove(t.id);
                    if (threads.isEmpty()) {
                        execution.onProgramEnd(o);
                        try {
                            this.execution.saveOwner();
                        } catch (Exception ex) {
                            LOGGER.log(Level.WARNING, "Error saving execution for "+this.getExecution(), ex);
                        }
                        ending = true;
                    }
                } else {
                    stillRunnable |= t.isRunnable();
                }
                changed = true;
            }
        }

        if (changed && !stillRunnable) {
            execution.persistedClean = null;
            saveProgramIfPossible(false);
        }
        if (ending) {
            execution.cleanUpHeap();
            if (scripts != null) {
                scripts.clear();
            }
            if (!closures.isEmpty()) {
                LOGGER.log(WARNING, "Stale closures in {0}: {1}", new Object[] {execution, closures.keySet()});
                closures.clear();
            }
            try {
                Util.deleteFile(execution.getProgramDataFile());
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, "Failed to delete program.dat in " + execution, x);
            }
        }

        return stillRunnable;
    }

    private transient List<FlowNode> nodesToNotify;
    private static final Object nodesToNotifyLock = new Object();
    /**
     * Notifies listeners of the new {@link FlowHead}.
     *
     * The actual call happens later from a place who owns no lock on any of the CPS objects to avoid deadlock.
     */
    @CpsVmThreadOnly
    /*package*/ void notifyNewHead(final FlowNode head) {
        assertVmThread();
        execution.notifyListeners(Collections.singletonList(head), true);
        synchronized (nodesToNotifyLock) {
            if (nodesToNotify == null) {
                nodesToNotify = new ArrayList<>();
            }
            nodesToNotify.add(head);
        }
        runner.execute(new Runnable() {
            public void run() {
                List<FlowNode> _nodesToNotify;
                synchronized (nodesToNotifyLock) {
                    if (nodesToNotify == null) {
                        return;
                    }
                    _nodesToNotify = nodesToNotify;
                    nodesToNotify = null;
                }
                execution.notifyListeners(_nodesToNotify, false);
            }
        });
    }

    public CpsThreadDump getThreadDump() {
        return CpsThreadDump.from(this);
    }

    /**
     * Like {@link #saveProgram()} but will not fail.
     * @param enteringQuietState True if we're moving to quiet state - pausing or quieting down and need to write the program.
     */
    @CpsVmThreadOnly
    void saveProgramIfPossible(boolean enteringQuietState) {
        if (this.getExecution() != null && (this.getExecution().getDurabilityHint().isPersistWithEveryStep()
                || enteringQuietState)) {

            try {  // Program may depend on flownodes being saved, so save nodes
                FlowNodeStorage storage = this.execution.getStorage();
                if (storage != null) {
                    storage.flush();
                }
            } catch (IOException ioe) {
                LOGGER.log(Level.WARNING, "Error persisting FlowNode storage before saving program", ioe);
            }

            try {
                saveProgram();
            } catch (IOException x) {
                LOGGER.log(WARNING, "program state save failed", x);
            }
        }
    }

    /**
     * Persists the current state of {@link CpsThreadGroup}.
     */
    @CpsVmThreadOnly
    void saveProgram() throws IOException {
        File f = execution.getProgramDataFile();
        saveProgram(f);
    }

    @SuppressFBWarnings(value="RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification="TODO 1.653+ switch to Jenkins.getInstanceOrNull")
    @CpsVmThreadOnly
    public void saveProgram(File f) throws IOException {
        File dir = f.getParentFile();
        File tmpFile = File.createTempFile("atomic",null, dir);

        assertVmThread();

        CpsFlowExecution old = PROGRAM_STATE_SERIALIZATION.get();
        PROGRAM_STATE_SERIALIZATION.set(execution);

        Collection<? extends PickleFactory> pickleFactories = PickleFactory.all();
        if (pickleFactories.isEmpty()) {
            LOGGER.log(WARNING, "Skipping save to {0} since Jenkins seems to be either starting up or shutting down", f);
            return;
        }

        boolean serializedOK = false;
        try (CpsFlowExecution.Timing t = execution.time(TimingKind.saveProgram)) {
            try (RiverWriter w = new RiverWriter(tmpFile, execution.getOwner(), pickleFactories)) {
                w.writeObject(this);
            }
            serializedOK = true;
            Files.move(tmpFile.toPath(), f.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.log(FINE, "program state saved");
        } catch (RuntimeException e) {
            propagateErrorToWorkflow(e);
            throw new IOException("Failed to persist "+f,e);
        } catch (IOException e) {
            if (!serializedOK) {
                propagateErrorToWorkflow(e);
            } // JENKINS-29656: otherwise just send the I/O error to caller and move on
            throw new IOException("Failed to persist "+f,e);
        } finally {
            PROGRAM_STATE_SERIALIZATION.set(old);
            Util.deleteFile(tmpFile);
        }
    }

    @CpsVmThreadOnly
    String asXml() {
        XStream xs = new XStream();
        // Could not handle a general PickleFactory without doing something weird with XStream
        // and there is no apparent way to make a high-priority generic Convertor delegate to others.
        // Anyway the only known exceptions are ThrowablePickle, which we are unlikely to need,
        // and RealtimeJUnitStep.Pickler which could probably be replaced by a DescribablePickleFactory
        // (and anyway these Describable objects would be serialized fine by XStream, just not JBoss Marshalling).
        for (SingleTypedPickleFactory<?> stpf : ExtensionList.lookup(SingleTypedPickleFactory.class)) {
            Class<?> factoryType = Functions.getTypeParameter(stpf.getClass(), SingleTypedPickleFactory.class, 0);
            xs.registerConverter(new Converter() {
                @Override public void marshal(Object source, HierarchicalStreamWriter writer, MarshallingContext context) {
                    Pickle p = stpf.writeReplace(source);
                    assert p != null : "failed to pickle " + source + " using " + stpf;
                    context.convertAnother(p);
                }
                @Override public Object unmarshal(HierarchicalStreamReader reader, UnmarshallingContext context) {
                    throw new UnsupportedOperationException(); // unused
                }
                @SuppressWarnings("rawtypes")
                @Override public boolean canConvert(Class type) {
                    return factoryType.isAssignableFrom(type);
                }
            });
        }
        // Could also register a convertor for FlowExecutionOwner, though it seems harmless.
        return xs.toXML(this);
    }

    /**
     * Propagates the failure to the workflow by passing an exception
     */
    @CpsVmThreadOnly
    private void propagateErrorToWorkflow(Throwable t) {
        // it's not obvious which thread to blame, so as a heuristics, pick up the last one,
        // as that's the ony more likely to have caused the problem.
        // TODO: when we start tracking which thread is just waiting for the body, then
        // that information would help. or maybe we should just remember the thread that has run the last time
        Map.Entry<Integer,CpsThread> lastEntry = threads.lastEntry();
        if (lastEntry != null) {
            lastEntry.getValue().resume(new Outcome(null,t));
        } else {
            LOGGER.log(Level.WARNING, "encountered error but could not pass it to the flow", t);
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CpsThreadGroup.class.getName());

    private static final long serialVersionUID = 1L;

    /**
     * CPS transformed program runs entirely inside a program execution thread.
     * If we are in that thread executing {@link CpsThreadGroup}, this method returns non-null.
     */
    @CpsVmThreadOnly
    /*package*/ static CpsThreadGroup current() {
        return CpsVmExecutorService.CURRENT.get();
    }
}
