package com.cloudbees.groovy.cps.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest;
import com.cloudbees.groovy.cps.Continuable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 * TODO: tests to write
 *   - catch order priority
 *
 * @author Kohsuke Kawaguchi
 */
public class TryCatchBlockTest extends AbstractGroovyCpsTest {
    @Test
    public void stackTraceFixup() throws Throwable {
        List<StackTraceElement> elements = List.of((StackTraceElement[]) evalCPSonly("\n" + "\n"
                + "def x() {\n"
                + "  y();\n"
                + // line 4
                "}\n"
                + "\n"
                + "def y() {\n"
                + "  throw new javax.naming.NamingException();\n"
                + // line 8
                "}\n"
                + "try {\n"
                + "  x();\n"
                + // line 11
                "} catch (Exception e) {\n"
                + "  return e.stackTrace;\n"
                + "}\n"));

        assertThat(
                elements.subList(0, 3).stream().map(Object::toString).collect(Collectors.toList()),
                hasItems(
                        "Script1.y(Script1.groovy:8)",
                        "Script1.x(Script1.groovy:4)",
                        "Script1.run(Script1.groovy:11)"));

        assertThat(elements.get(3), equalTo(Continuable.SEPARATOR_STACK_ELEMENT));

        List<String> rest = elements.subList(4, elements.size()).stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        assertThat(rest, hasItem(containsString(FunctionCallBlock.class.getName())));
        assertThat(rest, hasItem(containsString("java.lang.reflect.Constructor.newInstance")));
    }

    /**
     * Try block with finally clause completing normally.
     */
    @Test
    public void tryAndFinally_NormalCompletion() throws Throwable {
        assertEvaluate(
                "13",
                "a = '';\n" + "try {\n"
                        + "    a += '1';\n"
                        + "} catch (Exception e) {\n"
                        + "    a += '2';\n"
                        + "} finally {\n"
                        + "    a += '3';\n"
                        + "}\n"
                        + "return a;\n");
    }

    @Test
    public void tryWithoutFinally_NormalCompletion() throws Throwable {
        assertEvaluate(
                "1",
                "a = '';\n" + "try {\n"
                        + "    a += '1';\n"
                        + "} catch (Exception e) {\n"
                        + "    a += '2';\n"
                        + "}\n"
                        + "return a;\n");
    }

    @Test
    public void tryAndFinally_AbnormalTermination() throws Throwable {
        assertEvaluate(
                "1foo23",
                "a = '';\n" + "try {\n"
                        + "    a += '1';\n"
                        + "    throw new Exception('foo');\n"
                        + "    a += '2';\n"
                        + "} catch (Exception e) {\n"
                        + "    a += e.message + '2';\n"
                        + "} finally {\n"
                        + "    a += '3';\n"
                        + "}\n"
                        + "return a;\n");
    }

    @Test
    public void tryAndFinally_BreakInside() throws Throwable {
        assertEvaluate(
                "014",
                "a = '';\n" + "while (true) {\n"
                        + "    a += '0'\n"
                        + "    try {\n"
                        + "        a += '1';\n"
                        + "        break;\n"
                        + "        a += '2';\n"
                        + "    } catch (Exception e) {\n"
                        + "        a += '3';\n"
                        + "    } finally {\n"
                        + "        a += '4';\n"
                        + "    }\n"
                        + "    a += '5';\n"
                        + "}\n"
                        + "return a;\n");
    }

    @Test
    public void tryAndFinally_ContinueInside() throws Throwable {
        assertEvaluate(
                "0125125",
                "a = '';\n" + "a += '0'\n"
                        + "for (int i=0; i<2; i++) {\n"
                        + "    a += '1'\n"
                        + "    try {\n"
                        + "        a += '2';\n"
                        + "        continue;\n"
                        + "        a += '3';\n"
                        + "    } catch (Exception e) {\n"
                        + "        a += '4';\n"
                        + "    } finally {\n"
                        + "        a += '5';\n"
                        + "    }\n"
                        + "    a += '6';\n"
                        + "}\n"
                        + "return a;\n");
    }

    /**
     * Groovy interpreter seems to have a bug in running the finally block when an exception is thrown
     * from the catch block, so not using "evalCPS".
     */
    @Test
    public void tryAndFinally_RethrowAndFinallyBlock() throws Throwable {
        assertEvaluate(
                "135",
                "a = '';\n" + "try {\n"
                        + "    try {\n"
                        + "        a += '1';\n"
                        + "        throw new Exception('foo');\n"
                        + "        a += '2';\n"
                        + "    } catch (Exception e) {\n"
                        + "        a += '3';\n"
                        + "        throw new RuntimeException();\n"
                        + "        a += '4';\n"
                        + "    } catch (RuntimeException e) {\n"
                        + "        a += '6';\n"
                        + "    } finally {\n"
                        + "        a += '5';\n"
                        + "    }\n"
                        + "} catch (Exception e) {\n"
                        + "    ;\n"
                        + "}\n"
                        + "return a;\n");
    }

    @Test
    public void tryAndFinally_returnFromFinally() throws Throwable {
        assertEvaluate(
                "13",
                "a = '';\n" + "try {\n"
                        + "    a += '1';\n"
                        + "    throw new Exception('foo');\n"
                        + "    a += '2';\n"
                        + "} finally {\n"
                        + "    a += '3';\n"
                        + "    return a;\n"
                        + "}\n");
    }

    @Test
    public void tryAndFinally_returnFromCatch() throws Throwable {
        assertEvaluate(
                "13",
                "a = '';\n" + "try {\n"
                        + "    a += '1';\n"
                        + "    throw new Exception('foo');\n"
                        + "    a += '2';\n"
                        + "} catch (Exception e) {\n"
                        + "    a += '3';\n"
                        + "    return a;\n"
                        + "} finally {\n"
                        + "    a += '4';\n"
                        + "}\n");
    }

    @Test
    public void tryAndFinally_returnFromCatch2() throws Throwable {
        assertEquals(
                "134",
                evalCPSonly("a = new StringBuilder();\n" + "try {\n"
                                + "    a.append('1');\n"
                                + "    throw new Exception('foo');\n"
                                + "    a.append('2');\n"
                                + "} catch (Exception e) {\n"
                                + "    a.append('3');\n"
                                + "    return a;\n"
                                + "} finally {\n"
                                + "    a.append('4');\n"
                                + "}\n")
                        .toString());
    }
}
