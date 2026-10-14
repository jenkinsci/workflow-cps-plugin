package org.jenkinsci.plugins.workflow.cps;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import hudson.model.Action;
import hudson.model.Queue;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graph.FlowStartNode;
import org.jenkinsci.plugins.workflow.support.storage.FlowNodeStorage;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TimingFlowNodeStorageTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private static class DummyOwner extends FlowExecutionOwner {
        private final File rootDir;

        DummyOwner(File rootDir) {
            this.rootDir = rootDir;
        }

        @Override
        public FlowExecution get() throws IOException {
            return null;
        }

        @Override
        public File getRootDir() {
            return rootDir;
        }

        @Override
        public Queue.Executable getExecutable() throws IOException {
            return null;
        }

        @Override
        public String getUrl() throws IOException {
            return "job/test/1/";
        }

        @Override
        public boolean equals(Object o) {
            return this == o;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(this);
        }
    }

    private static class StubFlowNodeStorage extends FlowNodeStorage {
        private boolean persistedFully = true;

        @Override
        public FlowNode getNode(String id) throws IOException {
            return null;
        }

        @Override
        public void storeNode(FlowNode n) throws IOException {}

        @Override
        public void storeNode(FlowNode n, boolean delayWritingActions) throws IOException {}

        @Override
        public List<Action> loadActions(FlowNode node) throws IOException {
            return Collections.emptyList();
        }

        @Override
        public void saveActions(FlowNode node, List<Action> actions) throws IOException {}

        @Override
        public boolean isPersistedFully() {
            return persistedFully;
        }
    }

    @Test
    public void delegatesAvoidAtomicWriteAndIsPersistedFully() throws Exception {
        CpsFlowExecution maxSurvivability = new CpsFlowExecution(
                "echo 'test'", true, new DummyOwner(tmp.newFolder()), FlowDurabilityHint.MAX_SURVIVABILITY);
        assertFalse(maxSurvivability.getStorage().isAvoidAtomicWrite());

        CpsFlowExecution nonAtomic = new CpsFlowExecution(
                "echo 'test'", true, new DummyOwner(tmp.newFolder()), FlowDurabilityHint.SURVIVABLE_NONATOMIC);
        assertTrue(nonAtomic.getStorage().isAvoidAtomicWrite());

        CpsFlowExecution perfOptimized = new CpsFlowExecution(
                "echo 'test'", true, new DummyOwner(tmp.newFolder()), FlowDurabilityHint.PERFORMANCE_OPTIMIZED);
        assertTrue(perfOptimized.getStorage().isAvoidAtomicWrite());

        // Verify completed storage reload honors MAX_SURVIVABILITY atomic write configuration
        java.lang.reflect.Field storageDirField = CpsFlowExecution.class.getDeclaredField("storageDir");
        storageDirField.setAccessible(true);
        storageDirField.set(maxSurvivability, "workflow-completed");
        java.lang.reflect.Method createStorageMethod = CpsFlowExecution.class.getDeclaredMethod("createStorage");
        createStorageMethod.setAccessible(true);
        FlowNodeStorage completedStorage = (FlowNodeStorage) createStorageMethod.invoke(maxSurvivability);
        assertFalse(completedStorage.isAvoidAtomicWrite());

        // Verify optimizeStorage migrates to BulkFlowNodeStorage and preserves avoidAtomicWrite
        CpsFlowExecution toOptimize = new CpsFlowExecution(
                "echo 'test'", true, new DummyOwner(tmp.newFolder()), FlowDurabilityHint.MAX_SURVIVABILITY);
        FlowStartNode startNode = new FlowStartNode(toOptimize, "2");
        toOptimize.getStorage().storeNode(startNode, false);
        java.lang.reflect.Method optimizeMethod =
                CpsFlowExecution.class.getDeclaredMethod("optimizeStorage", FlowNode.class);
        optimizeMethod.setAccessible(true);
        optimizeMethod.invoke(toOptimize, startNode);
        assertTrue(
                "Expected BulkFlowNodeStorage after optimizeStorage",
                toOptimize.storage.delegate
                        instanceof org.jenkinsci.plugins.workflow.support.storage.BulkFlowNodeStorage);
        assertFalse(toOptimize.getStorage().isAvoidAtomicWrite());

        // Verify placeholder recovery creates fallback directory with SURVIVABLE_NONATOMIC when headsSerial is non-null
        CpsFlowExecution fallbackExec = new CpsFlowExecution(
                "echo 'test'", true, new DummyOwner(tmp.newFolder()), FlowDurabilityHint.SURVIVABLE_NONATOMIC);
        fallbackExec.getStorage();
        java.lang.reflect.Field headsSerialField = CpsFlowExecution.class.getDeclaredField("headsSerial");
        headsSerialField.setAccessible(true);
        headsSerialField.set(fallbackExec, Collections.singletonMap(1, "2"));
        fallbackExec.createPlaceholderNodes(new IOException("corrupt node"));
        assertTrue(fallbackExec.getStorageDir().getName().endsWith("-fallback"));
        assertTrue(fallbackExec.getStorageDir().isDirectory());
        assertTrue(new File(fallbackExec.getStorageDir(), "1.xml").isFile());
        assertTrue(fallbackExec.getStorage().isAvoidAtomicWrite());
        assertFalse(fallbackExec.getCurrentHeads().isEmpty());

        StubFlowNodeStorage delegate = new StubFlowNodeStorage();
        CpsFlowExecution.TimingFlowNodeStorage wrapper = maxSurvivability.new TimingFlowNodeStorage(delegate);

        assertFalse(delegate.isAvoidAtomicWrite());
        assertFalse(wrapper.isAvoidAtomicWrite());

        wrapper.setAvoidAtomicWrite(true);
        assertTrue("TimingFlowNodeStorage must forward setAvoidAtomicWrite to delegate", delegate.isAvoidAtomicWrite());
        assertTrue(wrapper.isAvoidAtomicWrite());

        delegate.persistedFully = false;
        assertFalse("TimingFlowNodeStorage must forward isPersistedFully to delegate", wrapper.isPersistedFully());
    }
}
