package com.cloudbees.groovy.cps.sandbox;

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest
import com.cloudbees.groovy.cps.Continuation
import com.cloudbees.groovy.cps.Env
import com.cloudbees.groovy.cps.impl.FunctionCallEnv;
import org.junit.Test
import org.kohsuke.groovy.sandbox.ClassRecorder;

/**
 * @author Kohsuke Kawaguchi
 */
public class SandboxInvokerTest extends AbstractGroovyCpsTest {
    def cr = new ClassRecorder()

    @Test
    public void basic() {
        assert 3==evalCpsSandbox("new String('abc'.bytes).length()")

        assertIntercept(
                "String.bytes",
                "new String(byte[])",
                "String.length()")
    }

    private Object evalCpsSandbox(String script) {
        FunctionCallEnv e = new FunctionCallEnv(null, null, null, null);
        e.invoker = new SandboxInvoker();

        cr.register()
        try {
            return parseCps(script).invoke(e, null, Continuation.HALT).run().yield.replay()
        } finally {
            cr.unregister()
        }
    }

    def assertIntercept(String... expected) {
        assertEquals(expected.join("\n"), cr.toString().trim())
    }
}
