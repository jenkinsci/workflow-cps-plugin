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
        List<StackTraceElement> elements = List.of((StackTraceElement[]) evalCPSonly("""
                    def x() {
                      y();
                    }

                    def y() {
                      throw new javax.naming.NamingException();
                    }
                    try {
                      x();
                    } catch (Exception e) {
                      return e.stackTrace;
                    }
                    """));

        assertThat(
                elements.subList(0, 3).stream().map(Object::toString).collect(Collectors.toList()),
                hasItems(
                        "Script1.y(Script1.groovy:6)", "Script1.x(Script1.groovy:2)", "Script1.run(Script1.groovy:9)"));

        assertThat(elements.get(3), equalTo(Continuable.SEPARATOR_STACK_ELEMENT));

        List<String> rest = elements.subList(4, elements.size()).stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        assertThat(rest, hasItem(containsString(FunctionCallBlock.class.getName())));
        assertThat(rest, hasItem(containsString("java.lang.reflect.Constructor.newInstance")));
    }
}
