package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest;
import javax.naming.NamingException;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for switch/case
 *
 * @author Kohsuke Kawaguchi
 */
public class SwitchBlockTest extends AbstractGroovyCpsTest {
    @Test
    public void basic() throws Throwable {
        assertEvaluate(
                "two",
                "def x = 2\n" + "def y\n"
                        + "switch (x) {\n"
                        + "case 1:\n"
                        + "    y = 'one'\n"
                        + "    break\n"
                        + "case 2:\n"
                        + "    y = 'two'\n"
                        + "    break\n"
                        + "case 3:\n"
                        + "    y = 'three'\n"
                        + "    break\n"
                        + "}\n"
                        + "return y\n");
    }

    /**
     * Null in the switch expression.
     */
    @Test
    public void nullSwitchExp() throws Throwable {
        assertEvaluate(
                "zero",
                "def x = null\n" + "def y = 'zero'\n"
                        + "switch (x) {\n"
                        + "case 1:\n"
                        + "    y = 'one'\n"
                        + "    break\n"
                        + "case 2:\n"
                        + "    y = 'two'\n"
                        + "    break\n"
                        + "case 3:\n"
                        + "    y = 'three'\n"
                        + "    break\n"
                        + "}\n"
                        + "return y\n");
    }

    /**
     * Null in the case expression.
     */
    @Test
    public void nullInCaseExp() throws Throwable {
        assertEvaluate(
                "null!",
                "def x = null\n" + "def y = 'zero'\n"
                        + "switch (x) {\n"
                        + "case 1:\n"
                        + "    y = 'one'\n"
                        + "    break\n"
                        + "case null:\n"
                        + "    y = 'null!'\n"
                        + "    break\n"
                        + "case 3:\n"
                        + "    y = 'three'\n"
                        + "    break\n"
                        + "}\n"
                        + "return y\n");
    }

    /**
     * Exception in the switch expression.
     */
    @Test
    public void exceptionInSwitchExp() throws Throwable {
        assertEvaluate(
                NamingException.class,
                "def foo() {\n" + "    throw new javax.naming.NamingException();\n"
                        + "}\n"
                        + "try {\n"
                        + "    switch (foo()) {\n"
                        + "    case 1:\n"
                        + "        y = 'one';\n"
                        + "        break;\n"
                        + "    case 2:\n"
                        + "        y = 'two!';\n"
                        + "        break;\n"
                        + "    }\n"
                        + "    return null;\n"
                        + "} catch (e) {\n"
                        + "    return e.class;\n"
                        + "}\n");
    }

    /**
     * Exception in the case expression.
     */
    @Test
    public void exceptionInCaseExp() throws Throwable {
        assertEvaluate(
                NamingException.class,
                "def foo() {\n" + "    throw new javax.naming.NamingException();\n"
                        + "}\n"
                        + "try {\n"
                        + "    switch (5) {\n"
                        + "    case 1:\n"
                        + "        y = 'one';\n"
                        + "        break;\n"
                        + "    case foo():\n"
                        + "        y = 'two';\n"
                        + "        break;\n"
                        + "    case 3:\n"
                        + "        y = 'three';\n"
                        + "        break;\n"
                        + "    }\n"
                        + "    return null;\n"
                        + "} catch (e) {\n"
                        + "    return e.class;\n"
                        + "}\n");
    }

    @Test
    public void isCase() throws Throwable {
        assertEvaluate(
                "odd",
                "def x = 5;\n" + "switch (x) {\n"
                        + "case 1:\n"
                        + "    y = 'one';\n"
                        + "    break;\n"
                        + "case [2,4,6,8]:\n"
                        + "    y = 'even';\n"
                        + "    break;\n"
                        + "case [3,5,7,9]:\n"
                        + "    y = 'odd';\n"
                        + "    break;\n"
                        + "}\n"
                        + "return y;\n");
    }

    /**
     * Two matching case statements.
     */
    @Test
    public void twoMatchingCases() throws Throwable {
        assertEvaluate(
                "two",
                "def x = 2;\n" + "def y;\n"
                        + "switch (x) {\n"
                        + "case 1:\n"
                        + "    y = 'one';\n"
                        + "    break;\n"
                        + "case 2:\n"
                        + "    y = 'two';\n"
                        + "    break;\n"
                        + "case 2:\n"
                        + "    y = 'TWO';\n"
                        + "    break;\n"
                        + "case 3:\n"
                        + "    y = 'three';\n"
                        + "    break;\n"
                        + "}\n"
                        + "return y;\n");
    }

    /**
     * Matches to the default clause
     */
    @Test
    public void defaultClause() throws Throwable {
        assertEvaluate(
                "other",
                "def x = 5;\n" + "def y;\n"
                        + "switch (x) {\n"
                        + "case 1:\n"
                        + "    y = 'one';\n"
                        + "    break;\n"
                        + "default:\n"
                        + "    y = 'other';\n"
                        + "    break;\n"
                        + "case 2:\n"
                        + "    y = 'two';\n"
                        + "    break;\n"
                        + "case 3:\n"
                        + "    y = 'three';\n"
                        + "    break;\n"
                        + "}\n"
                        + "return y;\n");
    }

    /**
     * Matches to nothing
     */
    @Test
    public void noMatch() throws Throwable {
        assertEvaluate(
                "initial",
                "def x = 5;\n" + "def y = 'initial';\n"
                        + "switch (x) {\n"
                        + "case 1:\n"
                        + "    y = 'one';\n"
                        + "    break;\n"
                        + "case 2:\n"
                        + "    y = 'two';\n"
                        + "    break;\n"
                        + "case 3:\n"
                        + "    y = 'three';\n"
                        + "    break;\n"
                        + "}\n"
                        + "return y;\n");
    }

    /**
     * Case match and fall through the rest.
     */
    @Test
    public void fallthrough() throws Throwable {
        assertEvaluate(
                "onetwothree",
                "def x = 1;\n" + "def y = '';\n"
                        + "switch (x) {\n"
                        + "case 1:\n"
                        + "    y += 'one';\n"
                        + "    // fall through\n"
                        + "case 2:\n"
                        + "    y += 'two';\n"
                        + "    // fall through\n"
                        + "case 3:\n"
                        + "    y += 'three';\n"
                        + "    // fall through\n"
                        + "}\n"
                        + "return y;\n");
    }

    /**
     * Default match and fall through
     */
    @Test
    @Ignore("Groovy doesn't handle this correctly")
    public void fallthroughWithDefault() throws Throwable {
        assertEvaluate(
                "otheronetwothree",
                "def x = 9;\n" + "def y = '';\n"
                        + "switch (x) {\n"
                        + "default:\n"
                        + "    y += 'other';\n"
                        + "    // fall through\n"
                        + "case 1:\n"
                        + "    y += 'one';\n"
                        + "    // fall through\n"
                        + "case 2:\n"
                        + "    y += 'two';\n"
                        + "    // fall through\n"
                        + "case 3:\n"
                        + "    y += 'three';\n"
                        + "    // fall through\n"
                        + "}\n"
                        + "return y;\n");
    }
}
