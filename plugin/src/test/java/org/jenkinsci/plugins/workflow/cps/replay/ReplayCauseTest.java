package org.jenkinsci.plugins.workflow.cps.replay;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;

import hudson.model.CauseAction;
import hudson.model.TaskListener;
import hudson.util.StreamTaskListener;
import hudson.util.XStream2;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jvnet.hudson.test.JenkinsRule;
import org.junit.Rule;
import org.junit.Test;

public class ReplayCauseTest {

    @Rule
    public JenkinsRule j = new JenkinsRule();

    @Test
    public void replayCausePrintIsSafeBeforeOnAddedTo() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "p");
        p.setDefinition(new CpsFlowDefinition("echo 'hello'", false));
        WorkflowRun original = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        // construct cause, don't call onAddedTo yet.
        ReplayCause cause = new ReplayCause(original);
        assertNull(cause.getRun());

        // verify print() does not throw NPE.
        TaskListener listener = StreamTaskListener.fromStdout();
        cause.print(listener);

        // verify getOriginal() returns run after onAddedTo.
        original.addAction(new CauseAction(cause));
        assertNotNull(cause.getOriginal());
    }

    @Test
    public void replayCausePrintIsSafeAfterDeserializeBeforeOnLoad() throws Exception {
        WorkflowJob p = j.createProject(WorkflowJob.class, "p2");
        p.setDefinition(new CpsFlowDefinition("echo 'hello'", false));
        WorkflowRun original = j.assertBuildStatusSuccess(p.scheduleBuild2(0));

        // Create a new run and attach the cause so run is initially set.
        WorkflowRun newRun = j.assertBuildStatusSuccess(p.scheduleBuild2(0));
        ReplayCause cause = new ReplayCause(original);
        newRun.addAction(new CauseAction(cause));
        assertNotNull(cause.getRun());

        // serialize and deserialize the cause, which should clear the transient run field.
        XStream2 xs = new XStream2();
        String xml = xs.toXML(cause);

        Object obj = xs.fromXML(xml);
        if (obj instanceof ReplayCause) {
            ReplayCause deserialized = (ReplayCause) obj;
            assertNull(deserialized.getRun());

            // getOriginal() is safe to call and returns null until onLoad is called
            TaskListener listener = StreamTaskListener.fromStdout();
            deserialized.print(listener);

            // simulate Jenkins calling onLoad to reattach the run (Run.onload() is protected)
            deserialized.onLoad(newRun);
            assertNotNull(deserialized.getOriginal());
        }
    }
}
