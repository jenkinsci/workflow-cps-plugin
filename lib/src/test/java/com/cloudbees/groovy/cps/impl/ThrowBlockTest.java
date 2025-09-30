package com.cloudbees.groovy.cps.impl;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest;
import com.cloudbees.groovy.cps.Continuable;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class ThrowBlockTest extends AbstractGroovyCpsTest {
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
}
