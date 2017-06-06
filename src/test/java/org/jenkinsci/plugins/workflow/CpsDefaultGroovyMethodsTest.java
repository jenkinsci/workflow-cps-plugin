package org.jenkinsci.plugins.workflow;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.jvnet.hudson.test.BuildWatcher;
import org.jvnet.hudson.test.Issue;
import org.jvnet.hudson.test.JenkinsRule;

import java.util.ArrayList;
import java.util.List;

@RunWith(Parameterized.class)
@Issue("JENKINS-26481")
public class CpsDefaultGroovyMethodsTest {
    @ClassRule
    public static JenkinsRule r = new JenkinsRule();
    @ClassRule
    public static BuildWatcher buildWatcher = new BuildWatcher();

    private String testName;
    private String testCode;

    public CpsDefaultGroovyMethodsTest(String testName, String testCode) {
        this.testName = testName;
        this.testCode = testCode;
    }

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> generateParameters() {
        List<Object[]> params = new ArrayList<>();
        for (Object[] p : com.cloudbees.groovy.cps.CpsDefaultGroovyMethodsTest.generateParameters()) {
            String n = (String)p[0];
            // sum methods require invokeMethod, so blocked.
            if (!n.startsWith("sum")) {
                params.add(new Object[]{p[0], p[1]});
            }
        }
        return params;
    }

    @Test
    public void dgm() throws Exception {
        WorkflowJob j = r.createProject(WorkflowJob.class, testName);
        j.setDefinition(new CpsFlowDefinition(testCode, true));

        r.buildAndAssertSuccess(j);
    }
}
