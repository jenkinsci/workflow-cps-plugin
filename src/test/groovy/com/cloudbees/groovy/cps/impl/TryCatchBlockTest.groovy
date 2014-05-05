package com.cloudbees.groovy.cps.impl

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest
import com.cloudbees.groovy.cps.Continuable
import org.junit.Test

/**
 * TODO: tests to write
 *   - try block breaking/continuing, running the finally block
 *   - exception caught in a catch block, running the finally block
 *   - exception rethrown in a catch block, running the finally block but not caught in other handlers
 *   - return statement in the finally block, masking an exception
 *   - return statement in the catch block, masking an exception but running the finally block
 *   - catch order priority
 *
 * @author Kohsuke Kawaguchi
 */
class TryCatchBlockTest extends AbstractGroovyCpsTest {
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

    /**
     * Try block with finally clause completing normally.
     */
    @Test
    void tryAndFinally_NormalCompletion() {
        def x = evalCPSonly("""
            a = "";
            try {
                a += "1";
            } catch (Exception e) {
                a += "2";
            } finally {
                a += "3";
            }
            return a;
""")
        assert x=="13";
    }

    @Test
    void tryWithoutFinally_NormalCompletion() {
        def x = evalCPSonly("""
            a = "";
            try {
                a += "1";
            } catch (Exception e) {
                a += "2";
            }
            return a;
""")
        assert x=="1";
    }

    @Test
    void tryAndFinally_AbnormalTermination() {
        def x = evalCPSonly("""
            a = "";
            try {
                a += "1";
                throw new Exception("foo");
                a += "2";
            } catch (Exception e) {
                a += e.message + "2";
            } finally {
                a += "3";
            }
            return a;
""")
        assert x=="1foo23";
    }

    @Test
    void tryAndFinally_BreakInside() {
        def x = evalCPS("""
            a = "";
            while (true) {
                a += "0"
                try {
                    a += "1";
                    break;
                    a += "2";
                } catch (Exception e) {
                    a += "3";
                } finally {
                    a += "4";
                }
                a += "5";
            }
            return a;
""")
        assert x=="014";
    }
}
