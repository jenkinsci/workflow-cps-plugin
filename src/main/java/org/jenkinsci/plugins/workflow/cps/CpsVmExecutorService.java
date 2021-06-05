package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import hudson.Main;
import hudson.model.Computer;
import hudson.remoting.SingleLaneExecutorService;
import hudson.security.ACL;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.CheckForNull;
import jenkins.model.Jenkins;

/**
 * {@link ExecutorService} for running CPS VM.
 *
 * @author Kohsuke Kawaguchi
 * @see CpsVmThreadOnly
 */
class CpsVmExecutorService extends InterceptingExecutorService {
    private CpsThreadGroup cpsThreadGroup;

    public CpsVmExecutorService(CpsThreadGroup cpsThreadGroup) {
        super(new SingleLaneExecutorService(Computer.threadPoolForRemoting));
        this.cpsThreadGroup = cpsThreadGroup;
    }

    @Override
    protected Runnable wrap(final Runnable r) {
        return new Runnable() {
            @Override
            public void run() {
                ThreadContext context = setUp();
                try {
                    r.run();
                } catch (final Throwable t) {
                    reportProblem(t);
                    throw t;
                } finally {
                    tearDown(context);
                }
            }
        };
    }

    /**
     * {@link CpsVmExecutorService} is used to run CPS VM asynchronously by one thread.
     * None of the submissions of these tasks are monitoring their outcome, and an exception
     * here usually means something catastrophic to the state of CPS VM.
     * That makes it worth reporting.
     */
    private void reportProblem(Throwable t) {
        LOGGER.log(Level.WARNING, "Unexpected exception in CPS VM thread: " + cpsThreadGroup.getExecution(), t);
        cpsThreadGroup.getExecution().croak(t);
    }

    @Override
    protected <V> Callable<V> wrap(final Callable<V> r) {
        return new Callable<V>() {
            @Override
            public V call() throws Exception {
                ThreadContext context = setUp();
                try {
                    return r.call();
                } catch (final Throwable t) {
                    reportProblem(t);
                    throw t;
                } finally {
                    tearDown(context);
                }
            }
        };
    }

    private static class ThreadContext {
        final Thread thread;
        final String name;
        final ClassLoader classLoader;
        final CpsFlowExecution.Timing timing;
        ThreadContext(Thread thread, CpsFlowExecution execution) {
            this.thread = thread;
            this.name = thread.getName();
            this.classLoader = thread.getContextClassLoader();
            ORIGINAL_CONTEXT_CLASS_LOADER.set(classLoader);
            timing = execution.time(CpsFlowExecution.TimingKind.run);
        }
        void restore() {
            thread.setName(name);
            thread.setContextClassLoader(classLoader);
            ORIGINAL_CONTEXT_CLASS_LOADER.set(null);
            timing.close();
        }
    }

    private ThreadContext setUp() {
        CpsFlowExecution execution = cpsThreadGroup.getExecution();
        ACL.impersonate(execution.getAuthentication());
        CURRENT.set(cpsThreadGroup);
        cpsThreadGroup.busy = true;
        Thread t = Thread.currentThread();
        ThreadContext context = new ThreadContext(t, execution);
        t.setName("Running " + execution);
        assert cpsThreadGroup.getExecution() != null;
        if (cpsThreadGroup.getExecution().getShell() != null) {
            assert cpsThreadGroup.getExecution().getShell().getClassLoader() != null;
            t.setContextClassLoader(cpsThreadGroup.getExecution().getShell().getClassLoader());
        }
        CpsCallableInvocation.registerMismatchHandler(this::handleMismatch);
        return context;
    }

    private void handleMismatch(Object expectedReceiver, String expectedMethodName, Object actualReceiver, String actualMethodName) {
        Class receiverClass = expectedReceiver.getClass();
        if (Jenkins.get().getPluginManager().whichPlugin(receiverClass) != null) {
            // Plugin code is opaque to the mismatch detector.
            return;
        }
        String mismatchMessage = mismatchMessage(className(expectedReceiver), expectedMethodName, className(actualReceiver), actualMethodName);
        if (FAIL_ON_MISMATCH) {
            throw new IllegalStateException(mismatchMessage);
        } else {
            try {
                cpsThreadGroup.getExecution().getOwner().getListener().getLogger().println(mismatchMessage);
            } catch (IOException x) {
                LOGGER.log(Level.FINE, null, x);
            }
        }
    }

    private static @CheckForNull String className(@CheckForNull Object receiver) {
        if (receiver == null) {
            return null;
        } else if (receiver instanceof Class) {
            return ((Class) receiver).getName();
        } else {
            return receiver.getClass().getName();
        }
    }

    /**
     * Making false positives be fatal makes it much easier to detect mistakes here and in PCT.
     * But we would rather have this be nonfatal in production,
     * since there are sure to be some false positives in exotic situations not yet covered by tests.
     * (As well as some false negatives, but this is a best effort after all.)
     */
    static boolean FAIL_ON_MISMATCH = Main.isUnitTest;

    static String mismatchMessage(@CheckForNull String expectedReceiverClassName, String expectedMethodName, @CheckForNull String actualReceiverClassName, String actualMethodName) {
        StringBuilder b = new StringBuilder("expected to call ");
        if (expectedReceiverClassName != null) {
            b.append(expectedReceiverClassName).append('.');
        }
        b.append(expectedMethodName).append(" but wound up catching ");
        if (actualReceiverClassName != null) {
            b.append(actualReceiverClassName).append('.');
        }
        b.append(actualMethodName);
        return b.append("; see: https://jenkins.io/redirect/pipeline-cps-method-mismatches/").toString();
    }

    private void tearDown(ThreadContext context) {
        CURRENT.set(null);
        cpsThreadGroup.busy = false;
        context.restore();
        CpsFlowExecution execution = cpsThreadGroup.getExecution();
        if (isShutdown()) {
            execution.logTimings();
        }
        CpsCallableInvocation.registerMismatchHandler(null);
    }

    static ThreadLocal<CpsThreadGroup> CURRENT = new ThreadLocal<>();
    /** {@link Thread#getContextClassLoader} to be used for plugin code, as opposed to Groovy. */
    static ThreadLocal<ClassLoader> ORIGINAL_CONTEXT_CLASS_LOADER = new ThreadLocal<>();

    private static final Logger LOGGER = Logger.getLogger(CpsVmExecutorService.class.getName());
}
