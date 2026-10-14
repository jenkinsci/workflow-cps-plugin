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
            throw new IOException("not implemented");
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
        CpsFlowExecution execution = new CpsFlowExecution(
                "echo 'test'", true, new DummyOwner(tmp.newFolder()), FlowDurabilityHint.MAX_SURVIVABILITY);
        assertFalse(execution.getStorage().isAvoidAtomicWrite());

        StubFlowNodeStorage delegate = new StubFlowNodeStorage();
        CpsFlowExecution.TimingFlowNodeStorage wrapper = execution.new TimingFlowNodeStorage(delegate);

        assertFalse(delegate.isAvoidAtomicWrite());
        assertFalse(wrapper.isAvoidAtomicWrite());

        wrapper.setAvoidAtomicWrite(true);
        assertTrue("TimingFlowNodeStorage must forward setAvoidAtomicWrite to delegate", delegate.isAvoidAtomicWrite());
        assertTrue(wrapper.isAvoidAtomicWrite());

        delegate.persistedFully = false;
        assertFalse("TimingFlowNodeStorage must forward isPersistedFully to delegate", wrapper.isPersistedFully());
    }
}
