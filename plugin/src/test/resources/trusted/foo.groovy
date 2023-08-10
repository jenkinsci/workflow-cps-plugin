import org.jenkinsci.plugins.workflow.cps.CpsFlowExecutionTest

// try to touch a sensitive method, which may or may not fail
// depending on the context we run in
public static void attempt() {
    CpsFlowExecutionTest.SECRET = true;
}
