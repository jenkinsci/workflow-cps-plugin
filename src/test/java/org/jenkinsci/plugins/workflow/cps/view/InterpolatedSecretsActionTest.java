package org.jenkinsci.plugins.workflow.cps.view;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.graph.FlowNode;
import org.jenkinsci.plugins.workflow.graphanalysis.LinearScanner;
import org.jenkinsci.plugins.workflow.graphanalysis.NodeStepTypePredicate;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.LoggerRule;

import java.util.Collections;

public class InterpolatedSecretsActionTest {
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    @ClassRule
    public static JenkinsRule r = new JenkinsRule();

    @Rule
    public LoggerRule logging = new LoggerRule();

    private WorkflowJob p;
    @Before
    public void newProject() throws Exception {
        p = r.createProject(WorkflowJob.class);
    }

    @Test
    public void testStepSignature() throws Exception {
        p.setDefinition(new CpsFlowDefinition("monomorphListStep([[firstArg:'one', secondArg:'two'], [firstArg:'three', secondArg:'four']])", true));
        WorkflowRun b = r.assertBuildStatusSuccess(p.scheduleBuild2(0));
        LinearScanner scan = new LinearScanner();
        FlowNode node = scan.findFirstMatch(b.getExecution().getCurrentHeads().get(0), new NodeStepTypePredicate("monomorphListStep"));
        InterpolatedSecretsAction.InterpolatedWarnings warning =
                new InterpolatedSecretsAction.InterpolatedWarnings("monomorphListStep", Collections.emptyList(), b, node.getId());
        MatcherAssert.assertThat(warning.getStepSignature(), Matchers.is("monomorphListStep(data: [[firstArg: one, secondArg: two], [firstArg: three, secondArg: four]])"));
    }
}
