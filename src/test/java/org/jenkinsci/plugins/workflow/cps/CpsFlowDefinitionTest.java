package org.jenkinsci.plugins.workflow.cps;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

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
                "def sqrt(int x) { return Math.sqrt(x); }\n" + 
                "for (int i=0; i<10; i++) { sqrt(i); }",
                false);

        createExecution(flow);
        exec.start();
        exec.waitForSuspension();

        assertTrue(exec.isComplete());
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
        assertTrue(exec.isComplete());
        Throwable t = exec.getCauseOfFailure();
        assertEquals(Throwable.class, t.getClass());
        assertEquals("This is a fire drill, not a real fire", t.getMessage());
    }

}
