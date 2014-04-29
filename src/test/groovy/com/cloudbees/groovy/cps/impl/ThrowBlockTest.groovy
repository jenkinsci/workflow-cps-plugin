package com.cloudbees.groovy.cps.impl

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest
import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class ThrowBlockTest extends AbstractGroovyCpsTest {
    @Test
    void stackTraceFixup() {
        def elements = evalCPSonly("""
            @WorkflowMethod
            def x() {
              y();
            }
            @WorkflowMethod
            def y() {
              throw new javax.naming.NamingException();
            }
            try {
              x();
            } catch (Exception e) {
              return e.stackTrace;
            }
        """)
    }
}
