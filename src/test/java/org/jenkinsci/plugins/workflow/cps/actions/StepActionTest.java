package org.jenkinsci.plugins.workflow.cps.actions;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl;
import com.google.common.collect.Collections2;
import hudson.EnvVars;
import org.jenkinsci.plugins.credentialsbinding.impl.BindingStep;
import org.jenkinsci.plugins.workflow.actions.StepInfoAction;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.cps.CpsThread;
import org.jenkinsci.plugins.workflow.cps.nodes.DescriptorMatchPredicate;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.FlowScanningUtils;
import org.jenkinsci.plugins.workflow.graphanalysis.ForkScanner;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.jenkinsci.plugins.workflow.steps.EchoStep;
import org.jenkinsci.plugins.workflow.test.steps.SemaphoreStep;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Tests the input sanitization and step persistence here
 */
public class StepActionTest {
    // Run pipeline, with pauses for steps
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void testStringSafetyTest() throws Exception {
        String input = "I have a secret p4ssw0rd";
        HashMap<String,String> passwordBinding = new HashMap<String,String>();
        passwordBinding.put("mypass", "p4ssw0rd");
        Assert.assertTrue("Input with no variables is safe", StepAction.isStringSafe(input, new EnvVars(), Collections.EMPTY_SET));
        Assert.assertFalse("Input containing bound value is unsafe", StepAction.isStringSafe(input, new EnvVars(passwordBinding), Collections.EMPTY_SET));

        Assert.assertTrue("EnvVars that do not occur are safe", StepAction.isStringSafe("I have no passwords", new EnvVars(passwordBinding), Collections.EMPTY_SET));

        HashMap<String, String> safeBinding = new HashMap<String,String>();
        safeBinding.put("harmless", "secret");
        HashSet<String> safeVars = new HashSet<String>();
        safeVars.add("harmless");
        passwordBinding.put("harmless", "secret");
        Assert.assertTrue("Input containing whitelisted bound value is safe", StepAction.isStringSafe(input, new EnvVars(safeBinding), safeVars));
        Assert.assertFalse("Input containing one safe and one unsafe bound value is unsafe", StepAction.isStringSafe(input, new EnvVars(passwordBinding), safeVars));
    }

    @Test
    public void testBasicCreateAndSanitize() throws Exception {
        HashMap<String,String> passwordBinding = new HashMap<String,String>();
        passwordBinding.put("mypass", "p4ssw0rd");
        Map<String, Object> arguments = new HashMap<String,Object>();
        arguments.put("message", "I have a secret p4ssw0rd");

        StepAction stepAction = new StepAction(arguments, new EnvVars());
        Assert.assertEquals(false, stepAction.isModifiedBySanitization());
        Assert.assertEquals(arguments.get("message"), stepAction.getParameterValueOrReason("message"));
        Assert.assertEquals(1, stepAction.getParameters().size());
        Assert.assertEquals("I have a secret p4ssw0rd", stepAction.getParameters().get("message"));

        // Test sanitizing parameters now
        stepAction = new StepAction(arguments, new EnvVars(passwordBinding));
        Assert.assertEquals(true, stepAction.isModifiedBySanitization());
        Assert.assertEquals(StepAction.NotStoredReason.MASKED_VALUE, stepAction.getParameterValueOrReason("message"));
        Assert.assertEquals(1, stepAction.getParameters().size());
        Assert.assertEquals(StepInfoAction.NotStoredReason.MASKED_VALUE, stepAction.getParameters().get("message"));
    }

    @Test
    public void testBasicCredentials() throws Exception {
        String username = "bob";
        String password = "s3cr3t";
        UsernamePasswordCredentialsImpl c = new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, "test", "sample", username, password);
        CredentialsProvider.lookupStores(r.jenkins).iterator().next().addCredentials(Domain.global(), c);

        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "credentialed");
        job.setDefinition(new CpsFlowDefinition(
                "node{ withCredentials([[$class: 'UsernamePasswordMultiBinding', credentialsId: 'test',\n" +
                "                usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD']]) {\n" +
                "    //available as an env variable, but will be masked if you try to print it out any which way\n" +
                "    echo \"$PASSWORD'\" \n" +
                "    echo \"${env.USERNAME}\"\n" +
                "    echo \"bob\"\n" +
                "} }"
        ));
        WorkflowRun run  = job.scheduleBuild2(0).getStartCondition().get();
        r.waitForCompletion(run);
        FlowExecution exec = run.getExecution();
        String log = r.getLog(run);
        ForkScanner scanner = new ForkScanner();
        List<FlowNode> filtered = scanner.filteredNodes(exec, new DescriptorMatchPredicate(BindingStep.DescriptorImpl.class));

        // Check the binding step is OK
        Assert.assertEquals(4, filtered.size());
        FlowNode node = Collections2.filter(filtered, FlowScanningUtils.hasActionPredicate(StepAction.class)).iterator().next();
        StepAction act = node.getPersistentAction(StepAction.class);
        Assert.assertNotNull(act.getParameterValue("bindings"));
        Assert.assertNotNull(act.getParameters().get("bindings"));

        // Test that masking really does mask bound credentials appropriately
        filtered = scanner.filteredNodes(exec, new DescriptorMatchPredicate(EchoStep.DescriptorImpl.class));
        for (FlowNode f : filtered) {
            act = f.getPersistentAction(StepAction.class);
            Assert.assertEquals(StepInfoAction.NotStoredReason.MASKED_VALUE, act.getParameters().get("message"));
        }

        List<FlowNode> allStepped = scanner.filteredNodes(run.getExecution().getCurrentHeads(), FlowScanningUtils.hasActionPredicate(StepAction.class));
        Assert.assertEquals(5, allStepped.size());  // One StepAction per block or atomic step
    }

    @Test
    public void simpleSemaphoreStep() throws Exception {
        WorkflowJob job = r.jenkins.createProject(WorkflowJob.class, "p");
        job.setDefinition(new CpsFlowDefinition(
                "semaphore 'wait'"
        ));
        WorkflowRun run  = job.scheduleBuild2(0).getStartCondition().get();
        SemaphoreStep.waitForStart("wait/1", run);
        FlowNode semaphoreNode = run.getExecution().getCurrentHeads().get(0);
        CpsThread thread = CpsThread.current();
        SemaphoreStep.success("wait/1", null);
        r.waitForCompletion(run);
    }
}
