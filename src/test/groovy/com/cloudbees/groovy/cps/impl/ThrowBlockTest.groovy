package com.cloudbees.groovy.cps.impl

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest
import com.cloudbees.groovy.cps.Continuable
import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class ThrowBlockTest extends AbstractGroovyCpsTest {
    @Test
    void stackTraceFixup() {
        List elements = evalCPSonly("""
            @WorkflowMethod
            def x() {
              y();  // line 4
            }
            @WorkflowMethod
            def y() {
              throw new javax.naming.NamingException(); // line 8
            }
            try {
              x(); // line 11
            } catch (Exception e) {
              return e.stackTrace;
            }
        """)

//        println elements;

        assert elements.subList(0,3).join("\n")=="""
Script1.y(Script1.groovy:8)
Script1.x(Script1.groovy:4)
Script1.run(Script1.groovy:11)
        """.trim();

        assert elements[3] == Continuable.SEPARATOR_STACK_ELEMENT

        def rest = elements.subList(4,elements.size()).join("\n");
        assert rest.contains(FunctionCallBlock.class.name)
        assert rest.contains("java.lang.reflect.Constructor.newInstance")
    }
}
