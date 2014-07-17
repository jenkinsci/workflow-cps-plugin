package com.cloudbees.groovy.cps.sandbox;

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest
import com.cloudbees.groovy.cps.Continuation
import com.cloudbees.groovy.cps.CpsTransformer
import com.cloudbees.groovy.cps.Env
import com.cloudbees.groovy.cps.SandboxCpsTransformer
import com.cloudbees.groovy.cps.impl.FunctionCallEnv;
import org.junit.Test
import org.kohsuke.groovy.sandbox.ClassRecorder;

/**
 * @author Kohsuke Kawaguchi
 */
public class SandboxInvokerTest extends AbstractGroovyCpsTest {
    def cr = new ClassRecorder()

    @Override
    protected CpsTransformer createCpsTransformer() {
        new SandboxCpsTransformer()
    }

    /**
     * Covers all the intercepted operations.
     */
    @Test
    public void basic() {
        evalCpsSandbox("""
            import java.awt.Point;

            def p = new Point(1,3);     // constructor
            assert p.equals(p)          // method call
            assert 4 == p.x+p.y;        // property get
            p.x = 5;                    // property set
            assert 5 == p.@x;           // attribute get
            p.@x = 6;                   // attribute set

            def a = new int[3];
            a[1] = a[0]+7;              // array get & set
            assert a[1]==7;
        """)

        assertIntercept("""
new Point(Integer,Integer)
Point.equals(Point)
Point.x
Point.y
Double.plus(Double)
ScriptBytecodeAdapter:compareEqual(Integer,Double)
Point.x=Integer
Point.@x
ScriptBytecodeAdapter:compareEqual(Integer,Integer)
Point.@x=Integer
int[][Integer]
Integer.plus(Integer)
int[][Integer]=Integer
int[][Integer]
ScriptBytecodeAdapter:compareEqual(Integer,Integer)
"""
)
    }

    @Test
    public void mixtureOfNonTransformation() {
        assert 3==evalCpsSandbox("""
    @NonCPS
    def length(x) {
        return x.length();
    }

    return length("foo")
""")
        assertIntercept('Script1.length(String)','String.length()')
    }


    private Object evalCpsSandbox(String script) {
        FunctionCallEnv e = new FunctionCallEnv(null, Continuation.HALT, null, null);
        e.invoker = new SandboxInvoker();

        cr.register()
        try {
            return parseCps(script).invoke(e, null, Continuation.HALT).run().yield.replay()
        } finally {
            cr.unregister()
        }
    }

    def assertIntercept(String... expected) {
        assertEquals(expected.join("\n").trim(), cr.toString().trim())
    }
}
