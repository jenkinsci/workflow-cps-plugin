package com.cloudbees.groovy.cps.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest;
import java.util.List;
import java.util.stream.Collectors;
import javax.naming.NamingException;
import org.junit.Test;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class FunctionCallBlockTest extends AbstractGroovyCpsTest {
    /**
     * Synchronous code we call from test that throws an exception
     */
    public static void someSyncCode(int i) throws Exception {
        if (i > 0) someSyncCode(i - 1);
        else throw new NamingException();
    }

    @Test
    public void infiniteRecursion() {
        try {
            evalCPSonly("def thing = null\n" + "def getThing() {\n"
                    + "    return thing == null\n"
                    + "}\n"
                    + "def stuff = getThing()\n");
            fail("Should have thrown an exception");
        } catch (Throwable t) {
            assertThat(
                    t.toString(),
                    equalTo(
                            "java.lang.StackOverflowError: Excessively nested closures/functions at Script1.getThing(Script1.groovy:3) - look for unbounded recursion - call depth: 1025"));
        }
    }

    @Test
    public void stackTraceFixup() throws Throwable {
        List<StackTraceElement> elements = List.of((StackTraceElement[]) evalCPSonly("""
                                def x() {
                                  y()
                                }

                                def y() {
                                  FunctionCallBlockTest.someSyncCode(3)
                                }
                                try {
                                  x()
                                } catch (Exception e) {
                                  return e.stackTrace
                                }
                                """));

        List<String> traces = elements.stream().map(Object::toString).collect(Collectors.toList());

        // should include the transformed CPS part
        assertThat(
                traces,
                hasItems(
                        containsString("Script1.y(Script1.groovy:6)"),
                        containsString("Script1.x(Script1.groovy:2)"),
                        containsString("Script1.run(Script1.groovy:9)"),
                        containsString("___cps.transform___(Native Method)"),
                        containsString("com.cloudbees.groovy.cps.impl.ContinuationGroup.methodCall")));

        // should include the call stack of some sync code
        assertThat(
                traces,
                hasItem(
                        containsString(
                                "com.cloudbees.groovy.cps.impl.FunctionCallBlockTest.someSyncCode(FunctionCallBlockTest.java:")));
    }
}
