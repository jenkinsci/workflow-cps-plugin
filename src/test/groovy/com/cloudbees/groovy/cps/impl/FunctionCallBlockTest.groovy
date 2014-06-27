package com.cloudbees.groovy.cps.impl

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest
import org.junit.Test

import javax.naming.NamingException

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class FunctionCallBlockTest extends AbstractGroovyCpsTest {
    /**
     * Synchronous code we call from test that throws an exception
     */
    public static void someSyncCode(int i) {
        if (i>0)
            someSyncCode(i-1);
        else
            throw new NamingException();
    }

    @Test
    void stackTraceFixup() {
        List elements = evalCPSonly("""

            def x() {
              y();  // line 4
            }

            def y() {
              FunctionCallBlockTest.someSyncCode(3); // line 8
            }
            try {
              x(); // line 11
            } catch (Exception e) {
              return e.stackTrace;
            }
        """)

//        println elements;

        def trace = elements.join("\n")

        // should include the transformed CPS part
        assert trace.contains("""
Script1.y(Script1.groovy:8)
Script1.x(Script1.groovy:4)
Script1.run(Script1.groovy:11)
___cps.transform___(Native Method)
com.cloudbees.groovy.cps.impl.ContinuationGroup\$1.call""");

        // should include the call stack of some sync code
        assert trace.contains("com.cloudbees.groovy.cps.impl.FunctionCallBlockTest.someSyncCode(FunctionCallBlockTest.groovy:")
    }
}
