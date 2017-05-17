package com.cloudbees.groovy.cps.impl

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest
import com.cloudbees.groovy.cps.Continuable
import org.junit.Test

/**
 * TODO: tests to write
 *   - catch order priority
 *
 * @author Kohsuke Kawaguchi
 */
class TryCatchBlockTest extends AbstractGroovyCpsTest {
    @Test
    void stackTraceFixup() {
        List elements = evalCPSonly("""

            def x() {
              y();  // line 4
            }

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
        def x = evalCPS("""
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
        def x = evalCPS("""
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
        def x = evalCPS("""
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

    @Test
    void tryAndFinally_ContinueInside() {
        def x = evalCPS("""
            a = "";
            a += "0"
            for (int i=0; i<2; i++) {
                a += "1"
                try {
                    a += "2";
                    continue;
                    a += "3";
                } catch (Exception e) {
                    a += "4";
                } finally {
                    a += "5";
                }
                a += "6";
            }
            return a;
""")
        assert x=="0125125";
    }

    /**
     * Groovy interpreter seems to have a bug in running the finally block when an exception is thrown
     * from the catch block, so not using "evalCPS".
     */
    @Test
    void tryAndFinally_RethrowAndFinallyBlock() {
        def x = evalCPSonly("""
            a = "";
            try {
                try {
                    a += "1";
                    throw new Exception("foo");
                    a += "2";
                } catch (Exception e) {
                    a += "3";
                    throw new RuntimeException();
                    a += "4";
                } catch (RuntimeException e) {
                    a += "6";
                } finally {
                    a += "5";
                }
            } catch (Exception e) {
                ;
            }
            return a;
""")
        assert x=="135";
    }

    @Test
    void tryAndFinally_returnFromFinally() {
        def x = evalCPS("""
            a = "";
            try {
                a += "1";
                throw new Exception("foo");
                a += "2";
            } finally {
                a += "3";
                return a;
            }
""")
        assert x=="13";
    }

    @Test
    void tryAndFinally_returnFromCatch() {
        def x = evalCPS("""
            a = "";
            try {
                a += "1";
                throw new Exception("foo");
                a += "2";
            } catch (Exception e) {
                a += "3";
                return a;
            } finally {
                a += "4";
            }
""")
        assert x=="13";
    }

    @Test
    void tryAndFinally_returnFromCatch2() {
        def x = evalCPSonly("""
            a = new StringBuilder();
            try {
                a.append("1");
                throw new Exception("foo");
                a.append("2");
            } catch (Exception e) {
                a.append("3");
                return a;
            } finally {
                a.append("4");
            }
""")
        assert x.toString()=="134";
    }
}
