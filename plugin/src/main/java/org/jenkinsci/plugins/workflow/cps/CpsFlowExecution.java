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
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Envs;
import com.cloudbees.groovy.cps.Outcome;
import com.cloudbees.groovy.cps.sandbox.Invoker;
import com.cloudbees.jenkins.support.api.Component;
import com.cloudbees.jenkins.support.api.Container;
import com.cloudbees.jenkins.support.api.Content;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.Converter;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.UnmarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.io.HierarchicalStreamReader;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import groovy.lang.GroovyShell;
import hudson.ExtensionList;
import hudson.model.Action;
import hudson.model.Result;
import hudson.util.Iterators;
import jenkins.model.CauseOfInterruption;
import jenkins.model.Jenkins;
import org.jboss.marshalling.Unmarshaller;
import org.jenkinsci.plugins.workflow.actions.ErrorAction;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.flow.BlockableResume;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.flow.GraphListener;
import org.jenkinsci.plugins.workflow.graph.BlockEndNode;
import org.jenkinsci.plugins.workflow.graph.BlockStartNode;
import org.jenkinsci.plugins.workflow.graph.FlowEndNode;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.graphanalysis.DepthFirstScanner;
import org.jenkinsci.plugins.workflow.steps.FlowInterruptedException;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.support.concurrent.Futures;
import org.jenkinsci.plugins.workflow.support.concurrent.Timeout;
import org.jenkinsci.plugins.workflow.support.pickles.serialization.PickleResolver;
import org.jenkinsci.plugins.workflow.support.pickles.serialization.RiverReader;
import org.jenkinsci.plugins.workflow.support.storage.FlowNodeStorage;
import org.jenkinsci.plugins.workflow.support.storage.BulkFlowNodeStorage;
import org.jenkinsci.plugins.workflow.support.storage.SimpleXStreamFlowNodeStorage;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Stack;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import hudson.AbortException;
import hudson.BulkChange;
import hudson.Extension;
import hudson.Util;
import hudson.Functions;
import hudson.init.Terminator;
import hudson.model.Item;
import hudson.model.Job;
import hudson.model.Queue;
import hudson.model.Run;
import hudson.model.Saveable;
import hudson.model.User;
import hudson.security.ACL;
import hudson.security.AccessControlled;
import hudson.security.Permission;
import java.beans.Introspector;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import net.jcip.annotations.GuardedBy;

import org.acegisecurity.Authentication;
import org.acegisecurity.userdetails.UsernameNotFoundException;
import java.nio.charset.StandardCharsets;
import jenkins.util.SystemProperties;
import org.codehaus.groovy.GroovyBugError;
import org.jboss.marshalling.reflect.SerializableClassRegistry;

import static com.thoughtworks.xstream.io.ExtendedHierarchicalStreamWriterHelper.startNode;
import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.RUN;

import org.jenkinsci.plugins.workflow.flow.FlowExecutionList;
import org.jenkinsci.plugins.workflow.graph.FlowGraphWalker;
import org.kohsuke.accmod.restrictions.DoNotUse;

/**
 * {@link FlowExecution} implemented with Groovy CPS.
 *
 * <h2>State Transition</h2>
 * <p>
 * {@link CpsFlowExecution} goes through the following states:
 *
 * <pre>{@code
 *                                    +----------------------+
 *                                    |                      |
 *                                    v                      |
 * PERSISTED --> PREPARING --> SUSPENDED --> RUNNABLE --> RUNNING --> COMPLETE
 *                                             ^
 *                                             |
 *                                           INITIAL
 * }</pre>
 *
 * <dl>
 * <dt>INITIAL</dt>
 * <dd>
 *     When a new {@link CpsFlowExecution} is created, it starts from here.
 *
 *     When {@link #start()} method is called, we get one thread scheduled, and we arrive at RUNNABLE state.
 * </dd>
 * <dt>PERSISTED</dt>
 * <dd>
 *     {@link CpsFlowExecution} is on disk with its owner, for example in {@code build.xml} of the workflow run.
 *     Nothing exists in memory. For example, Jenkins is not running.
 *
 *     Transition from this into PREPARING is triggered outside our control by XStream using
 *     {@link ConverterImpl} to unmarshal {@link CpsFlowExecution}. {@link #onLoad()} is called
 *     at the end, and we arrive at the PREPARING state.
 * </dd>
 * <dt>PREPARING</dt>
 * <dd>
 *     {@link CpsFlowExecution} is in memory, but {@link CpsThreadGroup} isn't. We are trying to
 *     restore all the ephemeral pickles that are necessary to get workflow going again.
 *     {@link #programPromise} represents a promise of completing this state.
 *
 *     {@link PickleResolver} keeps track of this, and when it's all done, we arrive at SUSPENDED state.
 * </dd>
 * <dt>SUSPENDED</dt>
 * <dd>
 *     {@link CpsThreadGroup} is in memory, but all {@link CpsThread}s are {@linkplain CpsThread#isRunnable() not runnable},
 *     which means they are waiting for some conditions to trigger (such as a completion of a shell script that's executing,
 *     human approval, etc). {@link CpsFlowExecution} and {@link CpsThreadGroup} are safe to persist.
 *
 *     When a condition is met, {@link CpsThread#resume(Outcome)} is called, and that thread becomes runnable,
 *     and we move to the RUNNABLE state.
 * </dd>
 * <dt>RUNNABLE</dt>
 * <dd>
 *     Some of {@link CpsThread}s are runnable, but we aren't actually running. The conditions that triggered
 *     {@link CpsThread} is captured in {@link CpsThread#resumeValue}.
 *     As we get into this state, {@link CpsThreadGroup#scheduleRun()}
 *     should be called to schedule the execution.
 *     {@link CpsFlowExecution} and {@link CpsThreadGroup} are safe to persist in this state, just like in the SUSPENDED state.
 *
 *     When {@link CpsThreadGroup#runner} allocated a real Java thread to the execution, we move to the RUNNING state.
 * </dd>
 * <dt>RUNNING</dt>
 * <dd>
 *     A thread is inside {@link CpsThreadGroup#run()} and is actively mutating the object graph inside the script.
 *     This state continues until no threads are runnable any more.
 *     Only one thread executes {@link CpsThreadGroup#run()}.
 *
 *     In this state, {@link CpsFlowExecution} still need to be persistable (because generally we don't get to
 *     control when it is persisted), but {@link CpsThreadGroup} isn't safe to persist.
 *
 *     When the Java thread leaves {@link CpsThreadGroup#run()}, we move to the SUSPENDED state.
 * </dd>
 * <dt>COMPLETE</dt>
 * <dd>
 *     All the {@link CpsThread}s have terminated and there's nothing more to execute, and there's no more events to wait.
 *     The result is finalized and there's no further state change.
 * </dd>
 * </dl>
 *
 * @author Kohsuke Kawaguchi
 */
@PersistIn(RUN)
public class CpsFlowExecution extends FlowExecution implements BlockableResume {
    /**
     * If {@code true}, then when the execution completes, we migrate the flow node storage from
     * {@link SimpleXStreamFlowNodeStorage} to {@link BulkFlowNodeStorage}.
     */
    @SuppressFBWarnings(value = "MS_SHOULD_BE_FINAL", justification = "non-final for modification via script console")
    public static boolean OPTIMIZE_STORAGE_UPON_COMPLETION = SystemProperties.getBoolean(CpsFlowExecution.class.getName() + ".OPTIMIZE_STORAGE_UPON_COMPLETION", true);

    /**
     * Groovy script of the main source file (that the user enters in the GUI)
     */
    private final String script;

    /**
     * Any additional scripts {@linkplain CpsGroovyShell#parse(GroovyCodeSource) parsed} afterward, keyed by
     * their FQCN.
     */
    /*package*/ /*final*/ Map<String,String> loadedScripts = new LinkedHashMap<>();

    private final boolean sandbox;
    private transient /*almost final*/ FlowExecutionOwner owner;

    /**
     * Loading of the program is asynchronous because it requires us to re-obtain stateful objects.
     * This object represents a {@link Future} for filling in {@link CpsThreadGroup}.
     *
     * TODO: provide a mechanism to diagnose how far along this process is.
     *
     * @see #runInCpsVmThread(FutureCallback)
     */
    @SuppressFBWarnings(value = "PA_PUBLIC_PRIMITIVE_ATTRIBUTE", justification = "TODO clean up")
    public transient volatile ListenableFuture<CpsThreadGroup> programPromise;
    private transient volatile Collection<ListenableFuture<?>> pickleFutures;

    /**
     * Recreated from {@link #owner}
     */
    /*package*/ transient /*almost final*/ TimingFlowNodeStorage storage;

    /** User ID associated with this build, or null if none specific. */
    private final @CheckForNull String user;

    /** If true, we did a clean write of FlowNode storage before shutdown. */
    Boolean persistedClean = null;

    boolean resumeBlocked = false;

    /**
     * Whether {@link CpsThreadGroup#isPaused} when loaded from disk.
     * @see #loadProgramAsync
     * @see #afterStepExecutionsResumed
     */
    private transient boolean pausedWhenLoaded;

    /** Subdirectory string where we store {@link FlowNode}s */
    private String storageDir = null;


    /**
     * Start nodes that have been created, whose {@link BlockEndNode} is not yet created.
     */
    @GuardedBy("this")
    /*package*/ /* almost final*/ Stack<BlockStartNode> startNodes = new Stack<>();
    @SuppressFBWarnings({"IS_FIELD_NOT_GUARDED", "IS2_INCONSISTENT_SYNC"}) // irrelevant here
    private transient List<String> startNodesSerial; // used only between unmarshal and onLoad

    @GuardedBy("this")
    /* almost final*/ NavigableMap<Integer,FlowHead> heads = new TreeMap<>(); // Non-private for unit tests

    @SuppressFBWarnings({"IS_FIELD_NOT_GUARDED", "IS2_INCONSISTENT_SYNC"}) // irrelevant here
    private transient Map<Integer,String> headsSerial; // used only between unmarshal and onLoad

    private final AtomicInteger iota = new AtomicInteger();

    /** Number of node IDs to use in lookup table, 500 covers most common flow graphs  */
    private static final int ID_LOOKUP_TABLE_SIZE = 500;

    /** Preallocated lookup table for small ID values, used instead of interning for speed & simplicity */
    private static final String[] ID_LOOKUP_TABLE = new String[ID_LOOKUP_TABLE_SIZE];

    static {
        for(int i = 0; i< ID_LOOKUP_TABLE.length; i++) {
            ID_LOOKUP_TABLE[i] = String.valueOf(i).intern();  // Interning here allows allows us to just intern on deserialize
        }
    }

    private transient List<GraphListener> listeners;

    /**
     * Result set from {@link StepContext}. Start by success and progressively gets worse.
     */
    private Result result = Result.SUCCESS;

    /**
     * When the program is completed, set to true.
     *
     * {@link FlowExecution} gets loaded into memory for the build records that have been completed,
     * and for those we don't want to load the program state, so that check should be efficient.
     */
    boolean done; // Only non-private for unit test use.

    /**
     * Groovy compiler with CPS+sandbox transformation correctly setup.
     * By the time the script starts running, this field is set to non-null.
     * It is reset to null after completion.
     */
    private transient CpsGroovyShell shell;

    /**
     * Groovy compiler wih CPS transformation but not sandbox.
     * Used by plugins to insert code that runs outside sandbox.
     *
     * By the time the script starts running, this field is set to non-null.
     * It is reset to null after completion.
     */
    private transient CpsGroovyShell trusted;

    /** Class of the {@link CpsScript}; its loader is a {@link groovy.lang.GroovyClassLoader.InnerLoader}, not the same as {@code shell.getClassLoader()}. */
    private transient Class<?> scriptClass;

    /** Actions to add to the {@link FlowStartNode}. */
    transient final List<Action> flowStartNodeActions = new ArrayList<>();

    /** If true, pipeline is forbidden to resume even if it can. */
    public boolean isResumeBlocked() {
        return resumeBlocked;
    }

    public void setResumeBlocked(boolean resumeBlocked) {
        if (this.resumeBlocked != resumeBlocked) {
            this.resumeBlocked = resumeBlocked;
        }
    }

    enum TimingKind {
        /**
         * Parsing Groovy sources; includes {@link #classLoad}.
         * @see CpsGroovyShell#parse(GroovyCodeSource)
         */
        parse,
        /**
         * Loading classes needed during {@link #parse}.
         * @see ClassLoader#loadClass(String, boolean)
         * @see ClassLoader#getResource
         */
        classLoad,
        /**
         * Running inside {@link CpsVmExecutorService}, which includes many other things.
         */
        run,
        /**
         * Time spent waiting in the queue for {@link CpsVmExecutorService}.
         */
        runQueue,
        /**
         * Saving the program state.
         * @see CpsThreadGroup#saveProgram(File)
         */
        saveProgram,
        /**
         * Loading or saving flow nodes.
         * @see FlowNodeStorage
         */
        flowNode
    }

    /** accumulated time in ns of a given {@link TimingKind#name}; {@link String} key for pretty XStream form */
    transient @NonNull Map<String, LongAdder> liveTimings = new ConcurrentHashMap<>();
    /** instances of {@link Timing} which have not yet completed for reporting counts and durations in support bundles. Never persisted. */
    transient @NonNull Set<Timing> liveIncompleteTimings = ConcurrentHashMap.newKeySet();
    /** XStream simplified form of {@link #liveTimings} */
    private Map<String, Long> timings;

    private @NonNull Set<String> internalCalls = ConcurrentHashMap.newKeySet();

    @Deprecated
    public CpsFlowExecution(String script, FlowExecutionOwner owner) throws IOException {
        this(script, false, owner);
    }

    public CpsFlowExecution(@NonNull String script, boolean sandbox, @NonNull  FlowExecutionOwner owner, @CheckForNull FlowDurabilityHint durabilityHint) throws IOException {
        this.owner = owner;
        this.script = script;
        this.sandbox = sandbox;
        this.durabilityHint = durabilityHint;
        Authentication auth = Jenkins.getAuthentication();
        this.user = auth.equals(ACL.SYSTEM) ? null : auth.getName();
        this.storage = createStorage();
        this.storage.setAvoidAtomicWrite(!this.getDurabilityHint().isAtomicWrite());
    }

    public CpsFlowExecution(String script, boolean sandbox, FlowExecutionOwner owner) throws IOException {
        this(script, sandbox, owner, null);
    }

    class Timing implements AutoCloseable {
        private final TimingKind kind;
        private final long start;
        private Timing(TimingKind kind) {
            this.kind = kind;
            start = System.nanoTime();
        }

        TimingKind getKind() {
            return kind;
        }

        long getStartNanos() {
            return start;
        }

        @Override public void close() {
            liveIncompleteTimings.remove(this);
            liveTimings.computeIfAbsent(kind.name(), k -> new LongAdder()).add(System.nanoTime() - start);
        }
    }

    /**
     * Record time taken during a certain class of operation in this build.
     * @param kind what sort of operation is being done
     * @return something to {@link Timing#close} when finished
     */
    Timing time(TimingKind kind) {
        var timing = new Timing(kind);
        liveIncompleteTimings.add(timing);
        return timing;
    }

    static final Logger TIMING_LOGGER = Logger.getLogger(CpsFlowExecution.class.getName() + ".timing");

    void logTimings() {
        if (TIMING_LOGGER.isLoggable(Level.FINE)) {
            Map<String, String> formatted = new TreeMap<>();
            liveTimings.forEach((k, v) -> formatted.put(k, v.longValue() / 1000 / 1000 + "ms"));
            TIMING_LOGGER.log(Level.FINE, "timings for {0}: {1}", new Object[] {owner, formatted});
        }
    }

    /**
     * Mark a call to an internal API made by this build.
     * @param call a representation of the call site; for example, {@code hudson.model.Run.setDescription}
     */
    void recordInternalCall(@NonNull String call) {
        internalCalls.add(call);
    }

    @NonNull Set<String> getInternalCalls() {
        return internalCalls;
    }

    /**
     * Returns a groovy compiler used to load the script.
     *
     * @see "doc/classloader.md"
     * @see GroovyShell#getClassLoader()
     */
    public GroovyShell getShell() {
        return shell;
    }

    /**
     * Returns a groovy compiler used to load the trusted script.
     *
     * @see "doc/classloader.md"
     */
    public GroovyShell getTrustedShell() {
        return trusted;
    }

    public FlowNodeStorage getStorage() {
        return storage;
    }

    public String getScript() {
        return script;
    }

    public Map<String,String> getLoadedScripts() {
        return Map.copyOf(loadedScripts);
    }

    /**
     * True if executing with groovy-sandbox, false if executing with approval.
     */
    public boolean isSandbox() {
        return sandbox;
    }

    @Override
    public FlowExecutionOwner getOwner() {
        return owner;
    }

    private TimingFlowNodeStorage createStorage() throws IOException {
        FlowNodeStorage wrappedStorage;

        if (this.storageDir != null && this.storageDir.endsWith("-completed")) {
           wrappedStorage = new BulkFlowNodeStorage(this, getStorageDir());
        } else {
            FlowDurabilityHint hint = getDurabilityHint();
            wrappedStorage = (hint.isPersistWithEveryStep())
                    ? new SimpleXStreamFlowNodeStorage(this, getStorageDir())
                    : new BulkFlowNodeStorage(this, getStorageDir());
        }
        return new TimingFlowNodeStorage(wrappedStorage);
    }

    /**
     * Called when the execution completes to migrate from {@link SimpleXStreamFlowNodeStorage} to
     * {@link BulkFlowNodeStorage} to improve read performance for completed builds.
     */
    private synchronized void optimizeStorage(FlowNode flowEndNode) {
        if (!OPTIMIZE_STORAGE_UPON_COMPLETION) {
            return;
        }
        if (storage.delegate instanceof SimpleXStreamFlowNodeStorage) {
            LOGGER.log(Level.FINE, () -> "Migrating " + this + " to BulkFlowNodeStorage");
            String newStorageDir = (this.storageDir != null) ? this.storageDir + "-completed" : "workflow-completed";
            try {
                FlowNodeStorage newStorage = new BulkFlowNodeStorage(this, new File(this.owner.getRootDir(), newStorageDir));
                DepthFirstScanner scanner = new DepthFirstScanner();
                scanner.setup(flowEndNode);
                // The hope is that by doing this right when the execution completes, most of the nodes will already be
                // cached in memory, saving us the cost of having to read them all from disk.
                for (FlowNode node : scanner) {
                    newStorage.storeNode(node, true);
                }
                newStorage.flush();
                LOGGER.log(Level.FINE, () -> "Copied nodes to " + newStorageDir);
                File oldStorageDir = getStorageDir();
                this.storageDir = newStorageDir;
                storage.readWriteLock.writeLock().lock();
                try {
                    storage.delegate = newStorage;
                    try {
                        Util.deleteRecursive(oldStorageDir);
                        LOGGER.log(Level.FINE, () -> "Deleted " + oldStorageDir);
                    } catch (IOException e) {
                        LOGGER.log(Level.FINE, e, () -> "Unable to delete unused flow node storage directory " + oldStorageDir + " for " + this);
                    }
                } finally {
                    storage.readWriteLock.writeLock().unlock();
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, e, () -> "Unable to migrate " + this + " to BulkFlowNodeStorage");
            }
        }
    }

    /**
     * Directory where workflow stores its state.
     */
    public File getStorageDir() throws IOException {
        return new File(this.owner.getRootDir(),
                (this.storageDir != null) ? this.storageDir : "workflow");
    }

    @Override
    public void start() throws IOException {
        final CpsScript s = parseScript();
        scriptClass = s.getClass();
        s.$initialize();

        final FlowHead h = new FlowHead(this);
        synchronized (this) {
            heads.put(h.getId(), h);
        }
        h.newStartNode(new FlowStartNode(this, iotaStr()));

        if (Thread.currentThread().isInterrupted()) {
            // We are intentionally using `isInterrupted` so interrupt status is visible to callers.
            // In Java 8, this should be unreachable, because if the thread was interrupted, `FlowHead.newStartNode`
            // would have thrown an exception (either ClosedByInterruptException or StreamException). In Java 11+, for
            // PERFORMANCE_OPTIMIZED Pipelines, `FlowHead.newStartNode` does not throw an exception if the thread is
            // interrupted, (I think because of changes to NIO internals), so we check for interruption explicitly.
            throw new InterruptedIOException(this + " was aborted while starting");
        }

        final CpsThreadGroup g = new CpsThreadGroup(this);

        g.register(s);
        final SettableFuture<CpsThreadGroup> f = SettableFuture.create();
        programPromise = f;
        saveOwner(); // Ensures we've saves the WorkFlowRun at least once with initial state
        g.runner.submit(new Runnable() {
            @Override
            public void run() {
                CpsThread t = g.addThread(new Continuable(s,createInitialEnv()),h,null);
                t.resume(new Outcome(null, null));
                f.set(g);
            }

            /**
             * Environment to start executing the script in.
             * During sandbox execution, we need to call sandbox interceptor while executing asynchronous code.
             */
            private Env createInitialEnv() {
                return Envs.empty(createInvoker());
            }
        });
    }

    Invoker createInvoker() {
        return LoggingInvoker.create(isSandbox());
    }

    private CpsScript parseScript() throws IOException {
        // classloader hierarchy. See doc/classloader.md
        CpsScript s;
        try {
            trusted = new CpsGroovyShellFactory(this).forTrusted().build();
            shell = new CpsGroovyShellFactory(this).withParent(trusted).build();

            s = (CpsScript) shell.reparse("WorkflowScript",script);

            for (Entry<String, String> e : loadedScripts.entrySet()) {
                shell.reparse(e.getKey(), e.getValue());
            }
        } catch (RuntimeException | Error x) {
            closeShells();
            throw x;
        }

        s.execution = this;
        return s;
    }

    /**
     * Assigns a new ID.
     */
    @Restricted(NoExternalUse.class)
    public String iotaStr() {
        int iotaVal = iota();
        // We intern this because many, many FlowNodes will have the same ID values
        if ( iotaVal > 0 && iotaVal < ID_LOOKUP_TABLE_SIZE) {
            return ID_LOOKUP_TABLE[iotaVal];
        } else {
            return String.valueOf(iotaVal).intern();
        }
    }

    @Restricted(NoExternalUse.class)
    public int iota() {
        return iota.incrementAndGet();
    }

    /**
     * Returns an approximate size of the flow graph, based on the heuristic that the iota is incremented once per new node.
     * The exact count may be a little different due to special cases.
     * ({@link FlowNodeStorage} does not currently offer a size, or a set of all nodes.
     * An exact count could be obtained with {@link FlowGraphWalker}, but this could be more overhead.)
     */
    int approximateNodeCount() {
        return iota.get();
    }

    /** For diagnostic purposes only, this logs current heads to assist with troubleshooting. */
    private synchronized String getHeadsAsString() {
        NavigableMap<Integer, FlowHead> myHeads = this.heads;
        if (myHeads == null) {
            return "null-heads";
        } else if (myHeads.size() == 0) {
            return "empty-heads";
        } else {
            return myHeads.entrySet().stream().map(h -> h.getKey() + "::" + h.getValue()).collect(Collectors.joining(","));
        }
    }

    /**
     * In the event we're missing FlowNodes, fail-fast and create some mockup FlowNodes so we can continue.
     * This avoids nulling out all of the execution's data
     * Bypasses {@link #croak(Throwable)} and {@link #onProgramEnd(Outcome)} to guarantee a clean path.
     */
    @GuardedBy("this")
    void createPlaceholderNodes(Throwable failureReason) throws Exception {
        synchronized (this) {
            this.done = true;

            if (this.owner != null) {
                // Ensure that the Run is marked as completed (failed) if it isn't already so it won't show as running
                Queue.Executable ex = owner.getExecutable();
                if (ex instanceof Run) {
                    Result res = ((Run) ex).getResult();
                    setResult(res != null ? res : Result.FAILURE);
                }
            }

            programPromise = Futures.immediateFailedFuture(new IllegalStateException("Failed loading heads", failureReason));
            LOGGER.log(Level.INFO, "Creating placeholder flownodes for execution: " + this);
            if (this.owner != null) {
                try {
                    owner.getListener().getLogger().println("Creating placeholder flownodes because failed loading originals.");
                } catch (Exception ex) {
                    // It's okay to fail to log
                }
            }

            // Switch to fallback storage so we don't delete original node data
            this.storageDir = (this.storageDir != null) ? this.storageDir + "-fallback" : "workflow-fallback";
            this.storage = createStorage();  // Empty storage

            // Clear out old start nodes and heads
            this.startNodes = new Stack<>();
            FlowHead head = new FlowHead(this);
            this.heads = new TreeMap<>();
            heads.put(head.getId(), head);
            FlowStartNode start = new FlowStartNode(this, iotaStr());
            head.newStartNode(start);

            // Create end
            FlowNode end = new FlowEndNode(this, iotaStr(), (FlowStartNode) startNodes.pop(), result, getCurrentHeads().toArray(new FlowNode[0]));
            end.addAction(new ErrorAction(failureReason));
            head.setNewHead(end);
        }
        saveOwner();
    }

    @SuppressFBWarnings(value = "IS2_INCONSISTENT_SYNC", justification="Storage does not actually NEED to be synchronized but the rest does.")
    protected synchronized void initializeStorage() throws IOException {
        storage = createStorage();
        heads = new TreeMap<>();
        for (Map.Entry<Integer,String> entry : headsSerial.entrySet()) {
            FlowHead h = new FlowHead(this, entry.getKey());

            FlowNode n = storage.getNode(entry.getValue());
            if (n != null) {
                h.setForDeserialize(storage.getNode(entry.getValue()));
                heads.put(h.getId(), h);
            } else {
                FlowDurabilityHint durabilitySetting = getDurabilityHint();
                if (durabilitySetting != FlowDurabilityHint.MAX_SURVIVABILITY) {
                    throw new AbortException("Cannot resume build because FlowNode " + entry.getValue() + " for FlowHead " + entry.getKey() + " could not be loaded. " +
                            "This is expected to happen when using the " + durabilitySetting + " durability setting and Jenkins is not shut down cleanly. " +
                            "Consider investigating to understand if Jenkins was not shut down cleanly or switching to the MAX_SURVIVABILITY durability setting which should prevent this issue in most cases.");
                } else {
                    throw new AbortException("Cannot resume build because FlowNode " + entry.getValue() + " for FlowHead " + entry.getKey() + " could not be loaded.");
                }
            }
        }
        headsSerial = null;

        startNodes = new Stack<>();
        for (String id : startNodesSerial) {
            FlowNode node = storage.getNode(id);
            if (node != null) {
                startNodes.add((BlockStartNode) storage.getNode(id));
            } else {
                // TODO if possible, consider trying to close out unterminated blocks using heads, to keep existing graph history
                throw  new IOException( "Tried to load startNode FlowNodes for execution "+this.owner+" but FlowNode was not found in storage for FlowNode Id "+id);
            }
        }
        startNodesSerial = null;
    }

    /** If true, we are allowed to resume the build because resume is enabled AND we shut down cleanly. */
    public boolean canResume() {
        if (isResumeBlocked()) {
            return false;
        }
        if (persistedClean != null) {
            return persistedClean;
        }
        FlowDurabilityHint hint = getDurabilityHint();
        return hint.isPersistWithEveryStep();
    }

    @Override
    @SuppressFBWarnings(value = "RC_REF_COMPARISON_BAD_PRACTICE_BOOLEAN", justification = "We want to explicitly check for boolean not-null and true")
    public void onLoad(FlowExecutionOwner owner) throws IOException {
        this.owner = owner;

        try {
            try {
                initializeStorage();  // Throws exception and bombs out if we can't load FlowNodes
            } catch (Exception ex) {
                LOGGER.log(Level.WARNING, "Error initializing storage and loading nodes, will try to create placeholders for: "+this, ex);
                createPlaceholderNodes(ex);
                return;
            }
        } catch (Exception ex) {
            done = true;
            programPromise = Futures.immediateFailedFuture(ex);
            throw new IOException("Failed to even create placeholder nodes for execution", ex);
        }

        try {
            if (isComplete()) {
                if (done == Boolean.TRUE && !super.isComplete()) {
                    LOGGER.log(Level.INFO, "Completed flow without FlowEndNode: "+this+" heads:"+getHeadsAsString());
                }
                if (super.isComplete() && done != Boolean.TRUE) {
                    LOGGER.log(Level.FINE, "Flow has FlowEndNode, but is not marked as done, fixing this for"+this);
                    done = true;
                    saveOwner();
                }
            } else {  // See if we can/should resume build
                if (canResume()) {
                    loadProgramAsync(getProgramDataFile());
                } else {
                    // TODO if possible, consider trying to close out unterminated blocks to keep existing graph history
                    // That way we can visualize the graph in some error cases.
                    LOGGER.log(Level.WARNING, "Pipeline state not properly persisted, cannot resume "+owner.getUrl());
                    throw new IOException("Cannot resume build -- was not cleanly saved when Jenkins shut down.");
                }
            }
        } catch (Exception e) {  // Broad catch ensures that failure to load do NOT nuke the controller
            SettableFuture<CpsThreadGroup> p = SettableFuture.create();
            programPromise = p;
            loadProgramFailed(e, p);
        } finally {
            if (programPromise == null) {
                programPromise = Futures.immediateFailedFuture(new IllegalStateException("completed or broken execution"));
            }
        }
    }

    /**
     * Deserializes {@link CpsThreadGroup} from {@link #getProgramDataFile()} if necessary.
     *
     * This moves us into the PREPARING state.
     * @param programDataFile
     */
    public void loadProgramAsync(File programDataFile) {
        final SettableFuture<CpsThreadGroup> result = SettableFuture.create();
        programPromise = result;

        try {
            scriptClass = parseScript().getClass();

            final RiverReader r = new RiverReader(programDataFile, scriptClass.getClassLoader(), owner);
            Futures.addCallback(
                    r.restorePickles(pickleFutures = new ArrayList<>()),

                    new FutureCallback<>() {
                        public void onSuccess(Unmarshaller u) {
                            pickleFutures = null;
                            try {
                            CpsFlowExecution old = PROGRAM_STATE_SERIALIZATION.get();
                            PROGRAM_STATE_SERIALIZATION.set(CpsFlowExecution.this);
                            try {
                                CpsThreadGroup g = (CpsThreadGroup) u.readObject();
                                result.set(g);
                                pausedWhenLoaded = g.isPaused();
                                g.pause(false);
                            } catch (Throwable t) {
                                onFailure(t);
                            } finally {
                                PROGRAM_STATE_SERIALIZATION.set(old);
                            }
                            } finally {
                                r.close();
                            }
                        }

                        public void onFailure(Throwable t) {
                            // Note: not calling result.setException(t) since loadProgramFailed in fact sets a result
                            try {
                                loadProgramFailed(t, result);
                            } finally {
                                r.close();
                            }
                        }
                    });

        } catch (Exception | GroovyBugError e) {
            loadProgramFailed(e, result);
        }
    }

    /**
     * Used to propagate a failure to load the persisted execution state.
     * @param promise same as {@link #programPromise} but more strongly typed
     */
    private void loadProgramFailed(final Throwable problem, SettableFuture<CpsThreadGroup> promise) {
        try {
            Functions.printStackTrace(problem, owner.getListener().getLogger());
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, x, () -> "failed to log problem to " + owner);
        }
        promise.setException(problem);
        croak(new AbortException("Failed to load program"));
    }

    /** Report a fatal error in the VM. */
    void croak(Throwable t) {
        setResult(Result.FAILURE);
        boolean noStartNodes;
        synchronized (this) {
            noStartNodes = startNodes == null || startNodes.isEmpty();
        }
        if (noStartNodes) {
            try {
                createPlaceholderNodes(t);
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, "Failed to create placeholder nodes in " + owner, x);
            }
        } else {
            onProgramEnd(new Outcome(null, t), true);
        }
        cleanUpHeap();
        try {
            saveOwner();
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Failed to persist WorkflowRun after noting a serious failure for run: " + owner, ex);
        }
    }

    @Override protected void afterStepExecutionsResumed() {
        runInCpsVmThread(new FutureCallback<>() {
            @Override public void onSuccess(CpsThreadGroup g) {
                try {
                    if (pausedWhenLoaded) {
                        owner.getListener().getLogger().println("Still paused");
                    } else {
                        owner.getListener().getLogger().println("Ready to run at " + new Date());
                        // In case we last paused execution due to Jenkins.isQuietingDown, make sure we do something after we restart.
                        g.unpause();
                        g.saveProgramIfPossible(false); // ensure pausedWhenLoaded=false is persisted
                    }
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
            }
            @Override public void onFailure(Throwable t) {
                LOGGER.log(Level.WARNING, "could not resume " + this, t);
            }
        });
    }

    /**
     * Where we store {@link CpsThreadGroup}.
     */
    /*package*/ File getProgramDataFile() throws IOException {
        return new File(owner.getRootDir(), "program.dat");
    }

    /**
     * Execute a task in {@link CpsVmExecutorService} to safely access {@link CpsThreadGroup} internal states.
     *
     * <p>
     * If the {@link CpsThreadGroup} deserialization fails, {@link FutureCallback#onFailure(Throwable)} will
     * be invoked (on a random thread, since CpsVmThread doesn't exist without a valid program.)
     */
    void runInCpsVmThread(final FutureCallback<CpsThreadGroup> callback) {
        if (programPromise == null) {
            throw new IllegalStateException("build storage unloadable, or build already finished");
        }
        // first we need to wait for programPromise to fullfil CpsThreadGroup, then we need to run in its runner, phew!
        Futures.addCallback(programPromise, new FutureCallback<>() {
            final Exception source = new Exception();   // call stack of this object captures who called this. useful during debugging.

            @Override
            public void onSuccess(final CpsThreadGroup g) {
                g.runner.submit(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess(g);
                    }
                });
            }

            /**
             * Program state failed to load.
             */
            @Override
            public void onFailure(Throwable t) {
                callback.onFailure(t);
            }
        });
    }

    /** See JENKINS-22941 for why this exists. */
    @Override public boolean blocksRestart() {
        if (programPromise == null || !programPromise.isDone()) {
            // Can't restart cleanly while trying to set up the build
            return true;
        }
        CpsThreadGroup g;
        try {
            g = programPromise.get();
        } catch (Exception x) {
            // TODO Check this won't cause issues due to depickling delays etc
            LOGGER.log(Level.FINE, "Not blocking restart due to exception in ProgramPromise: "+this, x);
            return false;
        }
        if (g.busy) {
            return true;
        } else {
            try {
                return getCurrentExecutions(false).get(1, TimeUnit.SECONDS).stream().anyMatch(StepExecution::blocksRestart);
            } catch (Exception x) {
                // TODO RestartListener.Default.isReadyToRestart can throw checked exceptions, but AsynchronousExecution.blocksRestart does not currently allow it
                Level level = x.getCause() instanceof RejectedExecutionException ? /* stray Executor past program end? */ Level.FINE : Level.WARNING;
                LOGGER.log(level, "Not blocking restart due to problem checking running steps in " + this, x);
                return false;
            }
        }
    }

    /**
     * Waits for the workflow to move into the SUSPENDED state.
     * @deprecated Use some other idiom, like {@code SemaphoreStep}.
     */
    @Deprecated
    public void waitForSuspension() throws InterruptedException, ExecutionException {
        if (programPromise==null)
            return; // the execution has already finished and we are not loading program state anymore
        CpsThreadGroup g = programPromise.get();
        // TODO occasionally tests fail here with RejectedExecutionException, apparently because the runner has been shut down; should we just ignore that?
        g.scheduleRun().get();
    }

    public synchronized @CheckForNull FlowHead getFlowHead(int id) {
        if (heads == null) {
            LOGGER.log(Level.WARNING, null, new IllegalStateException("List of flow heads unset for " + this));
            return null;
        }
        return heads.get(id);
    }

    @Override
    public synchronized List<FlowNode> getCurrentHeads() {
        if (heads == null) {
            LOGGER.log(Level.WARNING, null, new IllegalStateException("List of flow heads unset for " + this));
            return Collections.emptyList();
        }
        List<FlowNode> r = new ArrayList<>(heads.size());
        for (FlowHead h : heads.values()) {
            r.add(h.get());
        }
        return r;
    }

    @Override
    public ListenableFuture<List<StepExecution>> getCurrentExecutions(final boolean innerMostOnly) {
        if (programPromise == null || isComplete()) {
            return Futures.immediateFuture(Collections.<StepExecution>emptyList());
        }

        final SettableFuture<List<StepExecution>> r = SettableFuture.create();
        runInCpsVmThread(new FutureCallback<>() {
            @Override
            public void onSuccess(CpsThreadGroup g) {
                if (innerMostOnly) {
                    // to exclude outer StepExecutions, first build a map by FlowHead
                    // younger threads with their StepExecutions will overshadow old threads, leaving inner-most threads alone.
                    Map<FlowHead, StepExecution> m = new LinkedHashMap<>();
                    for (CpsThread t : g.getThreads()) {
                        StepExecution e = t.getStep();
                        if (e != null) {
                            m.put(t.head, e);
                        }
                    }
                    r.set(ImmutableList.copyOf(m.values()));
                } else {
                    List<StepExecution> es = new ArrayList<>();
                    for (CpsThread t : g.getThreads()) {
                        StepExecution e = t.getStep();
                        if (e != null) {
                            es.add(e);
                        }
                    }
                    r.set(Collections.unmodifiableList(es));
                }
            }

            @Override
            public void onFailure(Throwable t) {
                if (t instanceof RejectedExecutionException) {
                    // Program already suspended, fine
                    r.set(List.of());
                } else {
                    r.setException(t);
                }
            }
        });

        return r;
    }

    /**
     * Synchronously obtain the current state of the workflow program.
     *
     * <p>
     * The workflow can be already completed, or it can still be running.
     */
    public CpsThreadDump getThreadDump() {
        if (programPromise == null || isComplete()) {
            return CpsThreadDump.EMPTY;
        }
        if (!programPromise.isDone()) {
            // CpsThreadGroup state isn't ready yet, but this is probably one of the common cases
            // when one wants to obtain the stack trace. Cf. JENKINS-26130.
            Collection<ListenableFuture<?>> _pickleFutures = pickleFutures;
            if (_pickleFutures != null) {
                StringBuilder b = new StringBuilder("Program is not yet loaded");
                for (ListenableFuture<?> pickleFuture : _pickleFutures) {
                    b.append("\n\t").append(pickleFuture);
                    if (pickleFuture.isCancelled()) {
                        b.append(" (cancelled)");
                    }
                    if (pickleFuture.isDone()) {
                        b.append(" (complete)");
                    }
                }
                return CpsThreadDump.fromText(b.toString());
            } else {
                return CpsThreadDump.fromText("Program state is unknown");
            }
        }

        try {
            return programPromise.get().getThreadDump();
        } catch (InterruptedException e) {
            throw new AssertionError(); // since we are checking programPromise.isDone() upfront
        } catch (ExecutionException e) {
            return CpsThreadDump.from(new Exception("Failed to resurrect program state",e));
        }
    }

    @Override
    public synchronized boolean isCurrentHead(FlowNode n) {
        if (heads == null) {
            LOGGER.log(Level.WARNING, null, new IllegalStateException("List of flow heads unset for " + this));
            return false;
        }
        for (FlowHead h : heads.values()) {
            if (h.get().equals(n))
                return true;
        }
        return false;
    }

    /**
     * Called by FlowHead to add a new head.
     *
     * The new head gets removed via {@link #subsumeHead(FlowNode)} when it's used as a parent
     * of a FlowNode and thereby joining an another thread.
     */
    //
    void addHead(FlowHead h) {
        synchronized (this) {
            heads.put(h.getId(), h);
        }
        saveExecutionIfDurable();
    }

    void removeHead(FlowHead h) {
        synchronized (this) {
            heads.remove(h.getId());
        }
        saveExecutionIfDurable(); // We need to save the mutated heads for the run
    }

    /**
     * Removes a {@link FlowHead} that points to the given node from the 'current heads' list.
     *
     * This is used when a thread waits and collects the outcome of another thread.
     */
    void subsumeHead(FlowNode n) {
        List<FlowHead> _heads;
        synchronized (this) {
            _heads = new ArrayList<>(heads.values());
        }
        for (FlowHead h : _heads) {
            if (h.get()==n) {
                h.remove();
                saveExecutionIfDurable(); // We need to save the mutated heads for the run
                return;
            }
        }
    }


    @Override
    public void addListener(GraphListener listener) {
        if (listeners == null) {
            listeners = new CopyOnWriteArrayList<>();
        }
        listeners.add(listener);
    }

    @Override public void removeListener(GraphListener listener) {
        if (listeners != null) {
            listeners.remove(listener);
        }
    }

    @Override
    public void interrupt(Result result, CauseOfInterruption... causes) throws IOException, InterruptedException {
        setResult(result);

        LOGGER.log(Level.FINE, "Interrupting {0} as {1}", new Object[] {owner, result});
        final FlowInterruptedException ex = new FlowInterruptedException(result,causes);

        // stop all ongoing activities
        runInCpsVmThread(new FutureCallback<>() {
            @Override
            public void onSuccess(CpsThreadGroup g) {
                // don't touch outer ones. See JENKINS-26148
                Map<FlowHead, CpsThread> m = new LinkedHashMap<>();
                for (CpsThread t : g.getThreads()) {
                    m.put(t.head, t);
                }
                // for each inner most CpsThread, from young to old...
                for (CpsThread t : Iterators.reverse(List.copyOf(m.values()))) {
                    try {
                        t.stop(ex);
                    } catch (Exception x) {
                        LOGGER.log(Level.WARNING, "Failed to abort " + owner, x);
                    }
                }
            }

            @Override
            public void onFailure(Throwable t) {
                LOGGER.log(Level.WARNING, "Failed to interrupt steps in " + owner, t);
            }
        });

        // If we are still rehydrating pickles, try to stop that now.
        Collection<ListenableFuture<?>> futures = pickleFutures;
        if (futures != null) {
            LOGGER.log(Level.FINE, "We are still rehydrating pickles in {0}", owner);
            for (ListenableFuture<?> future : futures) {
                if (!future.isDone()) {
                    LOGGER.log(Level.FINE, "Trying to cancel {0} for {1}", new Object[] {future, owner});
                    if (!future.cancel(true)) {
                        LOGGER.log(Level.WARNING, "Failed to cancel {0} for {1}", new Object[] {future, owner});
                    }
                }
            }
        }
    }

    @Override
    public FlowNode getNode(String id) throws IOException {
        if (storage == null) {
            throw new IOException("storage not yet loaded");
        }
        return storage.getNode(id);
    }

    public void setResult(Result v) {
        result = result.combine(v);
    }

    public Result getResult() {
        return result;
    }

    @Override
    public List<Action> loadActions(FlowNode node) throws IOException {
        if (storage == null) {
            throw new IOException("storage not yet loaded");
        }
        return storage.loadActions(node);
    }

    @Override
    public void saveActions(FlowNode node, List<Action> actions) throws IOException {
        if (storage == null) {
            throw new IOException("storage not yet loaded");
        } else if (isComplete()) {
            throw new IOException("Cannot save actions for " + node + " for completed execution " + this + ": " + actions);
        }
        storage.saveActions(node, actions);
    }

    /** Stores FlowNode with write deferred */
    void cacheNode(@NonNull FlowNode node) {
        try {
            getStorage().storeNode(node, true);
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Attempt to persist triggered IOException for node "+node.getId(), ioe);
        }

    }

    /** Invoke me to toggle autopersist back on for steps that delay it. */
    public static void maybeAutoPersistNode(@NonNull FlowNode node) {
        try {
            FlowExecution exec = node.getExecution();
            if (exec instanceof CpsFlowExecution) {
                if (exec.getDurabilityHint().isPersistWithEveryStep()) {
                    FlowNodeStorage exc = ((CpsFlowExecution) exec).getStorage();
                    exc.autopersist(node);
                }
            }
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Attempt to persist triggered IOException for node "+node.getId(), ioe);
        }

    }

    @Override
    public boolean isComplete() {
        if (done) {
            return true;
        } else {
            synchronized (this) {
                return heads != null && super.isComplete();
            }
        }
    }

    /**
     * Record the end of the build.  Note: we should always follow this with a call to {@link #saveOwner()} to persist the result.
     * @param outcome success; or a normal failure (uncaught exception); or a fatal error in VM machinery
     */
    synchronized void onProgramEnd(Outcome outcome, boolean asynchNotifications) {
        FlowNode head = new FlowEndNode(this, iotaStr(), (FlowStartNode)startNodes.pop(), result, getCurrentHeads().toArray(new FlowNode[0]));
        if (outcome.isFailure()) {
            head.addAction(new ErrorAction(outcome.getAbnormal()));
        }

        // shrink everything into a single new head
        try {
            FlowHead first = getFirstHead();
            if (first != null) {
                first.setNewHead(head, asynchNotifications);
                done = true;  // After setting the final head
                heads.clear();
                heads.put(first.getId(), first);

                String tempIotaStr = Integer.toString(this.iota.get());
                FlowHead lastHead = heads.get(first.getId());
                if (lastHead == null || lastHead.get() == null || !(lastHead.get().getId().equals(tempIotaStr))) {
                    // Warning of problems with the final call to FlowHead.setNewHead
                    LOGGER.log(Level.WARNING, "Invalid final head for execution "+this.owner+" with head: "+lastHead);
                }
            }
        } catch (Exception ex) {
            done = true;
            LOGGER.log(Level.WARNING, "Error trying to end execution "+this, ex);
        }

        try {
            this.getStorage().flush();
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Error flushing FlowNodeStorage to disk at end of run", ioe);
        }
        this.optimizeStorage(head);

        this.persistedClean = Boolean.TRUE;
    }

    private void closeShells() {
        try {
            if (shell != null) {
                LOGGER.fine(() -> "closing main class loader from " + owner);
                shell.getClassLoader().close();
                shell = null;
            }
            if (trusted != null) {
                LOGGER.fine(() -> "closing trusted class loader from " + owner);
                trusted.getClassLoader().close();
                trusted = null;
            }
        } catch (IOException x) {
            LOGGER.log(Level.WARNING, "failed to close class loaders from " + owner, x);
        }
    }

    void cleanUpHeap() {
        LOGGER.log(Level.FINE, "cleanUpHeap on {0}", owner);
        closeShells();
        if (scriptClass != null) {
            try {
                cleanUpLoader(scriptClass.getClassLoader(), new HashSet<>(), new HashSet<>());
            } catch (Exception x) {
                LOGGER.log(Level.WARNING, "failed to clean up memory from " + owner, x);
            }
            scriptClass = null;
        } else {
            LOGGER.fine("no scriptClass");
        }
        // perhaps also set programPromise to null or a precompleted failure?
    }

    private static void cleanUpLoader(ClassLoader loader, Set<ClassLoader> encounteredLoaders, Set<Class<?>> encounteredClasses) throws Exception {
        if (loader instanceof CpsGroovyShell.TimingLoader) {
            cleanUpLoader(loader.getParent(), encounteredLoaders, encounteredClasses);
            return;
        }
        if (!(loader instanceof GroovyClassLoader)) {
            LOGGER.finer(() -> "ignoring " + loader);
            return;
        }
        if (!encounteredLoaders.add(loader)) {
            return;
        }
        cleanUpLoader(loader.getParent(), encounteredLoaders, encounteredClasses);
        LOGGER.finer(() -> "found " + loader);
        SerializableClassRegistry.getInstance().release(loader);
        cleanUpGlobalClassValue(loader);
        GroovyClassLoader gcl = (GroovyClassLoader) loader;
        for (Class<?> clazz : gcl.getLoadedClasses()) {
            if (encounteredClasses.add(clazz)) {
                LOGGER.finer(() -> "found " + clazz.getName());
                Introspector.flushFromCaches(clazz);
                cleanUpClassHelperCache(clazz);
                cleanUpLoader(clazz.getClassLoader(), encounteredLoaders, encounteredClasses);
            }
        }
        gcl.clearCache();
    }

    private static void cleanUpGlobalClassValue(@NonNull ClassLoader loader) throws Exception {
        Class<?> classInfoC = Class.forName("org.codehaus.groovy.reflection.ClassInfo");
        // TODO switch to MethodHandle for speed
        Field globalClassValueF = classInfoC.getDeclaredField("globalClassValue");
        globalClassValueF.setAccessible(true);
        Object globalClassValue = globalClassValueF.get(null);
        Class<?> groovyClassValuePreJava7C = Class.forName("org.codehaus.groovy.reflection.GroovyClassValuePreJava7");
        if (!groovyClassValuePreJava7C.isInstance(globalClassValue)) {
            return; // using GroovyClassValueJava7 due to -Dgroovy.use.classvalue or on IBM J9, fine
        }
        Field mapF = groovyClassValuePreJava7C.getDeclaredField("map");
        mapF.setAccessible(true);
        Object map = mapF.get(globalClassValue);
        Class<?> groovyClassValuePreJava7Map = Class.forName("org.codehaus.groovy.reflection.GroovyClassValuePreJava7$GroovyClassValuePreJava7Map");
        Collection entries = (Collection) groovyClassValuePreJava7Map.getMethod("values").invoke(map);
        Method removeM = groovyClassValuePreJava7Map.getMethod("remove", Object.class);
        Class<?> entryC = Class.forName("org.codehaus.groovy.util.AbstractConcurrentMapBase$Entry");
        Method getValueM = entryC.getMethod("getValue");
        List<Class<?>> toRemove = new ArrayList<>(); // not sure if it is safe against ConcurrentModificationException or not
        Field classRefF = classInfoC.getDeclaredField("classRef"); // 2.4.8+
        classRefF.setAccessible(true);
        for (Object entry : entries) {
            Object classInfo = getValueM.invoke(entry);
            if (classInfo != null) {
                Class<?> clazz = ((WeakReference<Class<?>>) classRefF.get(classInfo)).get();
                if (clazz != null) {
                    toRemove.add(clazz);
                }
            }
        }
        Iterator<Class<?>> it = toRemove.iterator();
        while (it.hasNext()) {
            Class<?> klazz = it.next();
            ClassLoader encounteredLoader = klazz.getClassLoader();
            if (encounteredLoader != loader) {
                it.remove();
                if (LOGGER.isLoggable(Level.FINEST)) {
                  LOGGER.finest(() -> "ignoring " + klazz + " with loader " + encounteredLoader);
                }
            }
        }
        LOGGER.fine(() -> "cleaning up " + toRemove + " associated with " + loader);
        for (Class<?> klazz : toRemove) {
            removeM.invoke(map, klazz);
        }
    }

    private static void cleanUpClassHelperCache(@NonNull Class<?> clazz) throws Exception {
        Field classCacheF = Class.forName("org.codehaus.groovy.ast.ClassHelper$ClassHelperCache").getDeclaredField("classCache");
        classCacheF.setAccessible(true);
        Object classCache = classCacheF.get(null);
        if (LOGGER.isLoggable(Level.FINER)) {
            LOGGER.log(Level.FINER, "cleaning up {0} from ClassHelperCache? {1}", new Object[] {clazz.getName(), classCache.getClass().getMethod("get", Object.class).invoke(classCache, clazz) != null});
        }
        classCache.getClass().getMethod("remove", Object.class).invoke(classCache, clazz);
    }

    synchronized @CheckForNull FlowHead getFirstHead() {
        if (heads == null) {
            return null;
        }
        Entry<Integer, FlowHead> firstEntry = heads.firstEntry();
        if (firstEntry == null) {
            return null;
        }
        return firstEntry.getValue();
    }

    List<GraphListener> getListenersToRun() {
        List<GraphListener> l = new ArrayList<>();

        if (listeners != null) {
            l.addAll(listeners);
        }
        l.addAll(ExtensionList.lookup(GraphListener.class));

        return l;
    }

    void notifyListeners(List<FlowNode> nodes, boolean synchronous) {
        List<GraphListener> toRun = getListenersToRun();

        if (!toRun.isEmpty()) {
            Saveable s = Saveable.NOOP;
            try {
                Queue.Executable exec = owner.getExecutable();
                if (exec instanceof Saveable) {
                    s = (Saveable) exec;
                }
            } catch (IOException x) {
                LOGGER.log(Level.WARNING, "failed to notify listeners of changes to " + nodes + " in " + this, x);
            }
            BulkChange bc = new BulkChange(s);
            try {
                for (FlowNode node : nodes) {
                    for (GraphListener listener : toRun) {
                        if (listener instanceof GraphListener.Synchronous == synchronous) {
                            try {
                                listener.onNewHead(node);
                            } catch (Throwable x) {
                                LOGGER.log(Level.WARNING, null, x);
                            }
                        }
                    }
                }
            } finally {
                if (synchronous) {
                    bc.abort(); // hack to skip save—we are holding a lock
                } else {
                    try {
                        bc.commit();
                    } catch (IOException x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }
            }
        }
    }

    @Override public Authentication getAuthentication() {
        if (user == null) {
            return ACL.SYSTEM;
        }
        try {
            User u = User.getById(user, true);
            if (u == null) {
                return Jenkins.ANONYMOUS;
            } else {
                return u.impersonate();
            }
        } catch (UsernameNotFoundException x) {
            LOGGER.log(Level.WARNING, "could not restore authentication", x);
            // Should not expose this to callers.
            return Jenkins.ANONYMOUS;
        }
    }

    /**
     * Finds the expected next loaded script name, like {@code Script1}.
     * @param path a file path being loaded (currently ignored)
     */
    @Restricted(NoExternalUse.class)
    public String getNextScriptName(String path) {
        return shell.generateScriptName().replaceFirst("[.]groovy$", "");
    }

    /** Has the execution been marked done - note that legacy builds may not have that flag persisted, in which case
     *  we look for a single FlowEndNode head (see: {@link #isComplete()} and {@link FlowExecution#isComplete()})
     */
    public boolean isDoneFlagSet() {
        return done;
    }

    public boolean isPaused() {
        if (programPromise.isDone()) {
            try {
                return programPromise.get().isPaused();
            } catch (ExecutionException | InterruptedException x) { // not supposed to happen
                LOGGER.log(Level.WARNING, null, x);
            }
        }
        return false;
    }

    private void setPersistedClean(boolean persistedClean) {  // Workaround for some issues with anonymous classes.
        this.persistedClean = persistedClean;
    }

    /**
     * Pause or unpause the execution.
     *
     * @param v
     *      true to pause, false to unpause.
     */
    public void pause(final boolean v) throws IOException {
        // TODO make FlowExecutionOwner implement AccessControlled (cf. PlaceholderTask.getACL):
        Queue.Executable executable = owner.getExecutable();
        if (executable instanceof AccessControlled) {
            ((AccessControlled) executable).checkPermission(Item.CANCEL);
        }
        done = false;
        Futures.addCallback(programPromise, new FutureCallback<>() {
            @Override public void onSuccess(CpsThreadGroup g) {
                if (v) {
                    g.pause(true);
                    checkAndAbortNonresumableBuild();  // TODO Verify if we can rely on just killing paused builds at shutdown via checkAndAbortNonresumableBuild()
                    checkpoint(false);
                } else {
                    g.unpause();
                }
                try {
                    owner.getListener().getLogger().println(v ? "Pausing" : "Resuming");
                } catch (IOException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
            }
            @Override public void onFailure(Throwable x) {
                LOGGER.log(Level.WARNING, "cannot pause/unpause " + this, x);
            }
        });
    }

    @Override public String toString() {
        return "CpsFlowExecution[" + owner + "]";
    }

    @Restricted(DoNotUse.class)
    @Terminator(attains = FlowExecutionList.EXECUTIONS_SUSPENDED)
    public static void suspendAll() {
        try (Timeout t = Timeout.limit(3, TimeUnit.MINUTES)) { // TODO some complicated sequence of calls to Futures could allow all of them to run in parallel
            LOGGER.fine("starting to suspend all executions");
            for (FlowExecution execution : FlowExecutionList.get()) {
                if (execution instanceof CpsFlowExecution) {
                    CpsFlowExecution cpsExec = (CpsFlowExecution) execution;
                    try {
                        cpsExec.checkAndAbortNonresumableBuild();

                        LOGGER.log(Level.FINE, "waiting to suspend {0}", execution);
                        // Like waitForSuspension but with a timeout:
                        if (cpsExec.programPromise != null) {
                            LOGGER.log(Level.FINER, "Waiting for Pipeline to go to sleep for shutdown: "+execution);
                            try {
                                cpsExec.programPromise.get(1, TimeUnit.MINUTES).scheduleRun().get(1, TimeUnit.MINUTES);
                                LOGGER.log(Level.FINER, " Pipeline went to sleep OK: "+execution);
                            } catch (InterruptedException | TimeoutException ex) {
                                LOGGER.log(Level.WARNING, "Error waiting for Pipeline to suspend: " + cpsExec, ex);
                            }
                        }
                        cpsExec.checkpoint(true);
                        if (cpsExec.programPromise != null) {
                            cpsExec.runInCpsVmThread(new FutureCallback<>() {
                                @Override public void onSuccess(CpsThreadGroup g) {
                                    LOGGER.fine(() -> "shutting down CPS VM threadin for " + cpsExec);
                                    g.shutdown();
                                }
                                @Override public void onFailure(Throwable t) {
                                    LOGGER.log(Level.WARNING, null, t);
                                }
                            });
                        }
                        if (cpsExec.owner != null) {
                            cpsExec.owner.getListener().getLogger().close();
                        }
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Error persisting Pipeline execution at shutdown: " + cpsExec.owner, ex);
                    }
                }
            }
            LOGGER.fine("finished suspending all executions");
        }
    }

    // TODO: write a custom XStream Converter so that while we are writing CpsFlowExecution, it holds that lock
    // the execution in Groovy CPS should hold that lock (or worse, hold that lock in the runNextChunk method)
    // so that the execution gets suspended while we are getting serialized

    // Note: XStream ignores readResolve and writeReplace methods on types with custom Converter implementations, so use marshal and unmarshal instead.

    public static final class ConverterImpl implements Converter {
        private final ReflectionProvider ref;
        private final Mapper mapper;

        public ConverterImpl(XStream xs) {
            this.ref = xs.getReflectionProvider();
            this.mapper = xs.getMapper();
        }

        public boolean canConvert(Class type) {
            return CpsFlowExecution.class==type;
        }

        public void marshal(Object source, HierarchicalStreamWriter w, MarshallingContext context) {
            CpsFlowExecution e = (CpsFlowExecution) source;

            writeChild(w, context, "result", e.result, Result.class);
            writeChild(w, context, "script", e.script, String.class);
            writeChild(w, context, "loadedScripts", e.loadedScripts, Map.class);
            if (e.persistedClean != null) {
                writeChild(w, context, "persistedClean", e.persistedClean, Boolean.class);
            }
            if (e.durabilityHint != null) {
                writeChild(w, context, "durabilityHint", e.durabilityHint, FlowDurabilityHint.class);
            }
            writeChild(w, context, "timings", e.liveTimings.entrySet().stream().collect(Collectors.toMap(kv -> kv.getKey(), kv -> kv.getValue().longValue())), Map.class);
            writeChild(w, context, "internalCalls", new TreeSet<>(e.internalCalls), Set.class);
            writeChild(w, context, "sandbox", e.sandbox, Boolean.class);
            if (e.user != null) {
                writeChild(w, context, "user", e.user, String.class);
            }
            writeChild(w, context, "iota", e.iota.get(), Integer.class);
            synchronized (e) {
                if (e.headsSerial != null && (e.heads == null || e.heads.isEmpty())) { // Persisting before onLoad has called to load up the real heads
                    // Note: unlike start nodes, heads should not be empty once onLoad loading has happened
                    for (Entry<Integer, String> entry : e.headsSerial.entrySet()) {
                        writeChild(w, context, "head", entry.getKey() + ":" + entry.getValue(), String.class);
                    }
                } else {  // onLoad was invoked, we have real heads
                    for (FlowHead h : e.heads.values()) {
                        writeChild(w, context, "head", h.getId() + ":" + h.get().getId(), String.class);
                    }
                }

                if (e.startNodesSerial != null && (e.startNodes == null)) {  // Start nodes have not been lazy-loaded yet
                    for (String startId : e.startNodesSerial) {
                        writeChild(w, context, "start", startId, String.class);
                    }
                } else {  // Start nodes were loaded, so we can convert back to serial form
                    for (BlockStartNode st : e.startNodes) {
                        writeChild(w, context, "start", st.getId(), String.class);
                    }
                }
                writeChild(w, context, "done", e.done, Boolean.class);
            }
            writeChild(w, context, "resumeBlocked", e.resumeBlocked, Boolean.class);

            if (e.storageDir != null) {
                writeChild(w, context, "storageDir", e.storageDir, String.class);
            }
        }

        private <T> void writeChild(HierarchicalStreamWriter w, MarshallingContext context, String name, @NonNull T v, Class<T> staticType) {
            if (!mapper.shouldSerializeMember(CpsFlowExecution.class,name))
                return;
            startNode(w, name, staticType);
            Class<?> actualType = v.getClass();
            if (actualType !=staticType)
                w.addAttribute(mapper.aliasForSystemAttribute("class"), mapper.serializedClass(actualType));

            context.convertAnother(v);
            w.endNode();
        }

        @SuppressFBWarnings(value = {"BX_UNBOXING_IMMEDIATELY_REBOXED", "RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE"}, justification = "Nastiness with the impl and timings variable could be null if deserialized from old version")
        public Object unmarshal(HierarchicalStreamReader reader, final UnmarshallingContext context) {
                CpsFlowExecution result;

                try {
                    if (context.currentObject() != null) {
                        result = (CpsFlowExecution) context.currentObject();
                    } else {
                        result = (CpsFlowExecution) ref.newInstance(CpsFlowExecution.class);
                    }

                    result.startNodesSerial = new ArrayList<>();
                    result.headsSerial = new TreeMap<>();
                    result.internalCalls = ConcurrentHashMap.newKeySet();

                    while (reader.hasMoreChildren()) {
                        reader.moveDown();

                        String nodeName = reader.getNodeName();
                        if (nodeName.equals("result")) {
                            Result r = readChild(reader, context, Result.class, result);
                            setField(result, "result", r);
                        } else if (nodeName.equals("script")) {
                            String script = readChild(reader, context, String.class, result);
                            setField(result, "script", script);
                        } else if (nodeName.equals("loadedScripts")) {
                            Map loadedScripts = readChild(reader, context, Map.class, result);
                            setField(result, "loadedScripts", loadedScripts);
                        } else if (nodeName.equals("timings")) {
                            Map timings = readChild(reader, context, Map.class, result);
                            setField(result, "timings", timings);
                        } else if (nodeName.equals("internalCalls")) {
                            Set internalCalls = readChild(reader, context, Set.class, result);
                            for (Object internalCall : internalCalls) {
                                result.internalCalls.add((String) internalCall);
                            }
                        } else if (nodeName.equals("sandbox")) {
                            boolean sandbox = readChild(reader, context, Boolean.class, result);
                            setField(result, "sandbox", sandbox);
                        } else if (nodeName.equals("owner")) {
                            readChild(reader, context, Object.class, result); // for compatibility; discarded
                        } else if (nodeName.equals("user")) {
                            String user = readChild(reader, context, String.class, result);
                            setField(result, "user", user);
                        } else if (nodeName.equals("head")) {
                            String[] head = readChild(reader, context, String.class, result).split(":");
                            result.headsSerial.put(Integer.parseInt(head[0]), head[1]);
                        } else if (nodeName.equals("iota")) {
                            Integer iota = readChild(reader, context, Integer.class, result);
                            setField(result, "iota", new AtomicInteger(iota));
                        } else if (nodeName.equals("done")) {
                            Boolean isDone = readChild(reader, context, Boolean.class, result);
                            setField(result, "done", isDone);
                        } else if (nodeName.equals("start")) {
                            String id = readChild(reader, context, String.class, result);
                            result.startNodesSerial.add(id);
                        } else if (nodeName.equals("durabilityHint")) {
                            FlowDurabilityHint hint = readChild(reader, context, FlowDurabilityHint.class, result);
                            setFieldParent(result, "durabilityHint", hint);
                        } else if (nodeName.equals("persistedClean")) {
                            Boolean hint = readChild(reader, context, Boolean.class, result);
                            setField(result, "persistedClean", hint);
                        } else if (nodeName.equals("resumeBlocked")) {
                            Boolean val = readChild(reader, context, Boolean.class, result);
                            setField(result, "resumeBlocked", val);
                        } else if (nodeName.equals("storageDir")) {
                            String val = readChild(reader, context, String.class, result);
                            setField(result, "storageDir", val);
                        }

                        reader.moveUp();
                    }

                    if (result.loadedScripts == null) {
                        result.loadedScripts = new LinkedHashMap<>(); // field added later
                    }
                    result.liveTimings = result.timings == null ?
                        new ConcurrentHashMap<>() :
                        result.timings.entrySet().stream().collect(Collectors.toConcurrentMap(kv -> kv.getKey(), kv -> {
                            LongAdder la = new LongAdder();
                            la.add(kv.getValue());
                            return la;
                        }));
                    result.liveIncompleteTimings = ConcurrentHashMap.newKeySet();
                    return result;
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Failed to even load the FlowExecution", ex);
                    throw new RuntimeException(ex);
                }
        }

        private void setField(CpsFlowExecution result, String fieldName, Object value) {
            ref.writeField(result, fieldName, value, CpsFlowExecution.class);
        }

        private void setFieldParent(CpsFlowExecution result, String fieldName, Object value) {
            ref.writeField(result, fieldName, value, FlowExecution.class);
        }

        /**
         * Called when a reader is
         */
        private <T> T readChild(HierarchicalStreamReader r, UnmarshallingContext context, Class<T> type, Object parent) {
            String classAttribute = r.getAttribute(mapper.aliasForAttribute("class"));
            if (classAttribute != null) {
                type = mapper.realClass(classAttribute);
            }

            return type.cast(context.convertAnother(parent, type));
        }
    }

    private static final Logger LOGGER = Logger.getLogger(CpsFlowExecution.class.getName());

    /**
     * While we serialize/deserialize {@link CpsThreadGroup} and the entire program execution state,
     * this field is set to {@link CpsFlowExecution} that will own it.
     */
    static final ThreadLocal<CpsFlowExecution> PROGRAM_STATE_SERIALIZATION = new ThreadLocal<>();

    class TimingFlowNodeStorage extends FlowNodeStorage {
        FlowNodeStorage delegate;
        private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();

        TimingFlowNodeStorage(FlowNodeStorage delegate) {
            this.delegate = delegate;
        }

        @Override
        public FlowNode getNode(String string) throws IOException {
            try (Timing t = time(TimingKind.flowNode)) {
                readWriteLock.readLock().lock();
                try {
                    return delegate.getNode(string);
                } finally {
                    readWriteLock.readLock().unlock();
                }
            }
        }

        @Override
        public void storeNode(@NonNull FlowNode n) throws IOException {
            try (Timing t = time(TimingKind.flowNode)) {
                readWriteLock.writeLock().lock();
                try {
                    delegate.storeNode(n);
                } finally {
                    readWriteLock.writeLock().unlock();
                }
            }
        }

        @Override
        public void storeNode(@NonNull FlowNode n, boolean delayWritingActions) throws IOException {
            try (Timing t = time(TimingKind.flowNode)) {
                readWriteLock.writeLock().lock();
                try {
                    delegate.storeNode(n, delayWritingActions);
                } finally {
                    readWriteLock.writeLock().unlock();
                }
            }
        }

        @Override
        public void flush() throws IOException {
            try (Timing t = time(TimingKind.flowNode)) {
                readWriteLock.writeLock().lock();
                try {
                    delegate.flush();
                } finally {
                    readWriteLock.writeLock().unlock();
                }
            }
        }

        @Override
        public void flushNode(FlowNode node) throws IOException {
            try (Timing t = time(TimingKind.flowNode)) {
                readWriteLock.writeLock().lock();
                try {
                    delegate.flushNode(node);
                } finally {
                    readWriteLock.writeLock().unlock();
                }
            }
        }

        @Override
        public void autopersist(@NonNull FlowNode n) throws IOException {
            try (Timing t = time(TimingKind.flowNode)) {
                readWriteLock.writeLock().lock();
                try {
                    delegate.autopersist(n);
                }  finally {
                    readWriteLock.writeLock().unlock();
                }
            }
        }

        @Override public List<Action> loadActions(FlowNode node) throws IOException {
            try (Timing t = time(TimingKind.flowNode)) {
                readWriteLock.readLock().lock();
                try {
                    return delegate.loadActions(node);
                } finally {
                    readWriteLock.readLock().unlock();
                }
            }
        }
        @Override public void saveActions(FlowNode node, List<Action> actions) throws IOException {
            try (Timing t = time(TimingKind.flowNode)) {
                readWriteLock.writeLock().lock();
                try {
                    delegate.saveActions(node, actions);
                } finally {
                    readWriteLock.writeLock().unlock();
                }
            }
        }
    }

    // If we wanted to expose via REST and/or floatingBox, could add a TransientActionFactory to show similar information.
    @Extension(optional=true) public static class PipelineTimings extends Component {

        @Override public Set<Permission> getRequiredPermissions() {
            return Set.of(Jenkins.ADMINISTER);
        }

        @Override public String getDisplayName() {
            return "Recently completed Pipeline builds";
        }

        @Override public ComponentCategory getCategory() {
            return ComponentCategory.BUILDS;
        }

        @Override public void addContents(Container container) {
            container.add(new Content("nodes/master/pipeline-recent-builds.txt") {
                @Override public void writeTo(OutputStream outputStream) throws IOException {
                    PrintWriter pw = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                    for (Job<?, ?> job : Jenkins.get().getAllItems(Job.class)) {
                        // TODO no clear way to tell if this might have Run instanceof FlowExecutionOwner.Executable, so for now just check for FlyweightTask which should exclude AbstractProject
                        if (job instanceof Queue.FlyweightTask) {
                            Run<?, ?> run = job.getLastCompletedBuild();
                            if (run instanceof FlowExecutionOwner.Executable) {
                                FlowExecutionOwner owner = ((FlowExecutionOwner.Executable) run).asFlowExecutionOwner();
                                if (owner != null) {
                                    FlowExecution exec;
                                    try {
                                        exec = owner.get();
                                    } catch (IOException x) {
                                        pw.println("No timings available for " + run + ": " + x);
                                        pw.println();
                                        continue;
                                    }
                                    if (exec instanceof CpsFlowExecution) {
                                        Map<String, LongAdder> sortedTimings = new TreeMap<>(((CpsFlowExecution) exec).liveTimings);
                                        pw.println("Timings for " + run + ":");
                                        sortedTimings.forEach((k, v) -> pw.println("  " + k + "\t" + v.longValue() / 1000 / 1000 + "ms"));
                                        pw.println("Approximate graph size: " + ((CpsFlowExecution) exec).approximateNodeCount());
                                        pw.println();
                                    }
                                }
                            }
                        }
                    }
                    pw.flush();
                }
            });
        }

    }

    @Extension(optional=true) public static class PipelineInternalCalls extends Component {

        @Override public Set<Permission> getRequiredPermissions() {
            return Collections.singleton(Jenkins.ADMINISTER);
        }

        @Override public String getDisplayName() {
            return "List of internal API calls made by Pipeline builds (typically from trusted libraries)";
        }

        @Override public ComponentCategory getCategory() {
            return ComponentCategory.BUILDS;
        }

        @Override public void addContents(Container container) {
            container.add(new Content("nodes/master/pipeline-internal-calls.txt") {
                @Override public void writeTo(OutputStream outputStream) throws IOException {
                    PrintWriter pw = new PrintWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                    pw.println("Internal Jenkins API calls from the last build of any job (plus one example of such a build):");
                    Map<String, String> internalCallsToExample = new TreeMap<>();
                    for (Job<?, ?> job : Jenkins.get().getAllItems(Job.class)) {
                        // TODO as above
                        if (job instanceof Queue.FlyweightTask) {
                            Run<?, ?> run = job.getLastCompletedBuild();
                            if (run instanceof FlowExecutionOwner.Executable) {
                                FlowExecutionOwner owner = ((FlowExecutionOwner.Executable) run).asFlowExecutionOwner();
                                if (owner != null) {
                                    FlowExecution exec = owner.getOrNull();
                                    if (exec instanceof CpsFlowExecution) {
                                        for (var call : ((CpsFlowExecution) exec).getInternalCalls()) {
                                            internalCallsToExample.putIfAbsent(call, run.toString());
                                        }
                                    }
                                }
                            }
                        }
                    }
                    for (var entry : internalCallsToExample.entrySet()) {
                        pw.println(entry.getKey() + " (" + entry.getValue() + ")");
                    }
                    pw.flush();
                }
            });
        }

    }

    /** Persist the execution if we are set up to save the execution with every step. */
    void saveExecutionIfDurable() {
        if (this.getDurabilityHint().isPersistWithEveryStep()) {
            saveOwner();
        }
    }

    /** Save the owner that holds this execution.
     *  Key note: to avoid deadlocks we need to ensure that we don't hold a lock on this CpsFlowExecution when running saveOwner
     *   or pre-emptively lock the run before locking the execution and saving. */
    void saveOwner() {
        try {
            if (this.owner != null && this.owner.getExecutable() instanceof Saveable) {  // Null-check covers some anomalous cases we've seen
                Saveable saveable = (Saveable)(this.owner.getExecutable());
                persistedClean = true;
                if (storage != null && storage.delegate != null) {
                    // Defensively flush FlowNodes to storage
                    try {
                        storage.flush();
                    } catch (Exception ex) {
                        LOGGER.log(Level.WARNING, "Error persisting FlowNodes for execution "+owner, ex);
                        persistedClean = false;
                    }
                }
                saveable.save();
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Error persisting Run "+owner, ex);
            persistedClean = false;
        }
    }

    /**
     * Save everything we can to disk - program, run, flownodes.
     * @param shuttingDown True if this checkpoint is happening because Jenkins is shutting down, false if it is happening because execution was paused.
     */
    private void checkpoint(boolean shuttingDown) {
        if (isComplete() || this.getDurabilityHint().isPersistWithEveryStep()) {
            // Nothing to persist OR we've already persisted it along the way.
            return;
        }
        LOGGER.log(Level.INFO, "Attempting to save a checkpoint of all data for {0}{1}", new Object[] {
            this, shuttingDown ? " before shutdown" : ""
        });
        boolean persistOk = true;
        FlowNodeStorage storage = getStorage();
        if (storage != null) {
            try { // Node storage must be flushed first so program can be restored
                storage.flush();
            } catch (IOException ioe) {
                persistOk=false;
                LOGGER.log(Level.WARNING, "Error persisting FlowNode storage for: " + this, ioe);
            }

            // Try to ensure we've saved the appropriate things -- the program is the last stumbling block.
            try {
                final CompletableFuture<Void> myOutcome = new CompletableFuture<>();
                LOGGER.log(Level.FINE, "About to try to checkpoint the program for: {0}", this);
                if (programPromise != null && programPromise.isDone()) {
                    runInCpsVmThread(new FutureCallback<>() {
                        @Override
                        public void onSuccess(CpsThreadGroup result) {
                            try {
                                LOGGER.log(Level.FINE, "Trying to save program for: {0}", CpsFlowExecution.this);
                                result.saveProgramIfPossible(true);
                                LOGGER.log(Level.FINE, "Finished saving program for: {0}", CpsFlowExecution.this);
                                myOutcome.complete(null);
                            } catch (Exception ex) {
                                // Logged at Level.WARNING when we call `myOutcome.get` and it throws an exception.
                                myOutcome.completeExceptionally(ex);
                            }
                        }

                        @Override
                        public void onFailure(Throwable t) {
                            // Logged at Level.WARNING when we call `myOutcome.get` and it throws an exception.
                            myOutcome.completeExceptionally(t);
                        }
                    });
                    myOutcome.get(30, TimeUnit.SECONDS);
                    LOGGER.log(Level.FINE, "Successfully saved program for: {0}", this);
                } else {
                    persistOk = false;
                    LOGGER.log(Level.WARNING, "Unable to persist program because it was never loaded for: {0}", this);
                }

            } catch (TimeoutException te) {
                persistOk = false;
                LOGGER.log(Level.WARNING, "Timeout persisting program for: " + this, te);
            } catch (ExecutionException | InterruptedException ex) {
                persistOk = false;
                LOGGER.log(Level.WARNING, "Error saving program for: " + this, ex);
            }
            try { // Flush node storage just in case the Program mutated it, just to be sure
                storage.flush();
                LOGGER.log(Level.FINE, "Successfully did final flush of storage for: {0}", this);
            } catch (IOException ioe) {
                persistOk=false;
                LOGGER.log(Level.WARNING, "Error persisting FlowNode storage for: " + this, ioe);
            }
            persistedClean = persistOk;
            try {
                saveOwner();
            } catch (Exception ex) {
                persistOk = false;
                LOGGER.log(Level.WARNING, "Error saving build for: " + this, ex);
            }

        } else {
            persistOk = false;
            LOGGER.log(Level.WARNING, "No FlowNode storage for: {0}", this);
        }

        if (persistOk) {
            LOGGER.log(Level.INFO, "Successfully checkpointed {0}{1}", new Object[] {
                this, (shuttingDown ? " before shutdown" : "")
            });
        } else {
            LOGGER.log(Level.WARNING, "Unable to successfully checkpoint {0}{1}", new Object[] {
                this, (shuttingDown ? " before shutdown, so this build will probably fail when Jenkins restarts" : "")
            });
        }
    }

    /** Abort any running builds at Jenkins shutdown if they don't support resuming at next startup. */
    private void checkAndAbortNonresumableBuild() {
        if (isComplete() || this.getDurabilityHint().isPersistWithEveryStep() || !isResumeBlocked()) {
            return;
        }
        try {
            owner.getListener().getLogger().println("Failing build: shutting down controller and build is marked to not resume");
            final Throwable x = new FlowInterruptedException(Result.ABORTED);
            Futures.addCallback(this.getCurrentExecutions(/* cf. JENKINS-26148 */true), new FutureCallback<>() {
                @Override public void onSuccess(List<StepExecution> l) {
                    for (StepExecution e : Iterators.reverse(l)) {
                        StepContext context = e.getContext();
                        context.onFailure(x);
                        try {
                            FlowNode n = context.get(FlowNode.class);
                            if (n != null) {
                                owner.getListener().getLogger().println("Terminating " + n.getDisplayFunctionName());
                            }
                        } catch (Exception x) {
                            LOGGER.log(Level.FINE, null, x);
                        }
                    }
                }

                @Override public void onFailure(Throwable t) {
                    LOGGER.log(Level.WARNING, "Error stopping build due to error obtaining executions", t);
                }
            });
        } catch (IOException ioe) {
            LOGGER.log(Level.WARNING, "Error just doing logging", ioe);
        }
    }

}
