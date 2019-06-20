package org.jenkinsci.plugins.workflow.cps;

import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class CpsFlowDefinitionTest extends AbstractCpsFlowTest {
    /**
     * CpsFlowDefinition's simplest possible test.
     */
    @Test
    public void simplestPossibleTest() throws Exception {
        CpsFlowDefinition flow = new CpsFlowDefinition(
                "def sqrt(int x) { return Math.sqrt(x); }\nfor (int i=0; i<10; i++)\nsqrt(i);",
                false);

        createExecution(flow);
        exec.start();
        exec.waitForSuspension();

        assert exec.isComplete();
    }

    @Test
    public void exceptionInWorkflowShouldBreakFlowExecution() throws Exception {
        CpsFlowDefinition flow = new CpsFlowDefinition(
                "throw new Throwable('This is a fire drill, not a real fire');",
                false);

        // get this going...
        createExecution(flow);
        exec.start();

        // it should stop at watch and suspend.
        exec.waitForSuspension();
        assert exec.isComplete();
        Throwable t = exec.getCauseOfFailure();
        assert t.getClass().equals(Throwable.class);
        assert t.getMessage().equals("This is a fire drill, not a real fire");
    }

}
