package com.cloudbees.groovy.cps;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import groovy.lang.Script;
import java.lang.reflect.InvocationTargetException;
import java.util.stream.Collectors;
import org.junit.Test;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class ContinuableTest extends AbstractGroovyCpsTest {
    @Test
    public void resumeAndSuspend() throws Throwable {
        Script s = getCsh().parse("int x = 1;\n" + "x = Continuable.suspend(x+1)\n" + "return x+1;\n");

        Continuable c = new Continuable(s);
        assertTrue(c.isResumable());

        Object v = c.run(null);
        assertEquals("Continuable.suspend(x+1) returns the control back to us", 2, v);

        assertTrue("Continuable is resumable because it has 'return x+1' to execute.", c.isResumable());
        assertEquals("We resume continuable, then the control comes back from the return statement", 4, c.run(3));

        assertFalse("We've run the program till the end, so it's no longer resumable", c.isResumable());
    }

    @Test
    public void serializeComplexContinuable() throws Throwable {
        Script s = getCsh().parse("def foo(int x) {\n" + "    return Continuable.suspend(x);\n"
                + "}\n"
                + "def plus3(int x) {\n"
                + "    return x+3;\n"
                + "}\n"
                + "try {\n"
                + "    for (int x=0; x<1; x++) {\n"
                + "        while (true) {\n"
                + "            y = plus3(foo(5))\n"
                + "            break;\n"
                + "        }\n"
                + "    }\n"
                + "} catch (ClassCastException e) {\n"
                + "    y = e;\n"
                + "}\n"
                + "return y;\n");

        Continuable c = new Continuable(s);
        assertEquals("suspension within a subroutine", 5, c.run(null));

        c = roundtripSerialization(
                c); // at this point there's a fairly non-trivial Continuation, so try to serialize it

        assertTrue(c.isResumable());
        assertEquals(9, c.run(6));
    }

    @Test
    public void howComeBindingIsSerializable() throws Throwable {
        Script s = getCsh().parse("Continuable.suspend(42);\n" + "return value;\n");
        s.setProperty("value", 15);
        Continuable c = new Continuable(s);
        assertEquals(42, c.run(null));

        c = roundtripSerialization(c);

        assertTrue(c.isResumable());
        assertEquals(15, c.run(null));
    }

    @Test
    public void suspend_at_the_end_should_still_count_as_resumable() throws Throwable {
        Script s = getCsh().parse("Continuable.suspend(5);");
        Continuable c = new Continuable(s);
        assertEquals(5, c.run(null));
        assertTrue(c.isResumable());
        c.run(null);
        assertFalse(c.isResumable());
    }

    /**
     * Exception not handled in script will be thrown from Continuable.run
     */
    @Test
    public void unhandled_exception() throws Throwable {
        Script s = getCsh().parse("throw new " + ContinuableTest.class.getName() + ".HelloException()");
        Continuable c = new Continuable(s);
        try {
            c.run(null);
            fail("should have thrown exception");
        } catch (InvocationTargetException e) {
            assertFalse(c.isResumable());
            assertThat(e.getCause(), instanceOf(HelloException.class));
        }
    }

    public static class HelloException extends Exception {}

    /**
     * Object passed to {@link Continuable#suspend(Object)} isn't accessible when Continuable
     * resumes, so it shouldn't be a part of the persisted object graph.
     */
    @Test
    public void yieldObjectShouldNotBeInObjectGraph() throws Throwable {
        Script s = getCsh().parse("Continuable.suspend(new ContinuableTest.ThisObjectIsNotSerializable());");
        Continuable c = new Continuable(s);
        Object r = c.run(null);
        assertThat(r, instanceOf(ThisObjectIsNotSerializable.class));

        c = roundtripSerialization(c);

        assertTrue(c.isResumable());
        assertEquals(42, c.run(42));
    }

    public static class ThisObjectIsNotSerializable {}

    /**
     * Tests {@link Continuable#getStackTrace()}.
     */
    @Test
    public void stackTrace() throws Throwable {
        Script s = getCsh().parse("\n" + "\n"
                + "def x(i,v) {\n"
                + "  if (i>0)\n"
                + "    y(i-1,v);\n"
                + // line 5
                "  else\n"
                + "    Continuable.suspend(v);\n"
                + // line 7
                "}\n"
                + "\n"
                + "def y(i,v) {\n"
                + "  if (i>0)\n"
                + "    x(i-1,v);\n"
                + // line 12
                "  else\n"
                + "    Continuable.suspend(v);\n"
                + // line 14
                "}\n"
                + "\n"
                + "x(5,3); // line 17\n");

        Continuable c = new Continuable(s);

        // stack trace is empty if it hasn't been started
        assertTrue(c.getStackTrace().isEmpty());

        Object v = c.run(null);
        assertEquals(3, v);

        assertThat(
                c.getStackTrace().stream().map(Object::toString).collect(Collectors.toList()),
                hasItems(
                        containsString("Script1.y(Script1.groovy:14)"),
                        containsString("Script1.x(Script1.groovy:5)"),
                        containsString("Script1.y(Script1.groovy:12)"),
                        containsString("Script1.x(Script1.groovy:5)"),
                        containsString("Script1.y(Script1.groovy:12)"),
                        containsString("Script1.x(Script1.groovy:5)"),
                        containsString("Script1.run(Script1.groovy:17)")));

        c.run(null);

        // stack trace is empty if there's nothing more to execute
        assertTrue(c.getStackTrace().isEmpty());
    }

    /**
     * Triggers the use of {@link org.codehaus.groovy.ast.expr.StaticMethodCallExpression}
     */
    @Test
    public void staticMethod1() throws Throwable {
        Script s = getCsh().parse("import static java.lang.Class.forName; forName('java.lang.Integer')");
        Continuable c = new Continuable(s);
        Object r = c.run(null);
        assertEquals(Integer.class, r);
    }

    /**
     * Static method call expression with two arguments
     */
    @Test
    public void staticMethod2() throws Throwable {
        Script s = getCsh().parse("import static java.lang.Integer.toString; toString(31,16)");
        Continuable c = new Continuable(s);
        Object r = c.run(null);
        assertEquals("1f", r);
    }

    /**
     * Static method call expression with no arguments
     */
    @Test
    public void staticMethod0() throws Throwable {
        Script s = getCsh().parse(
                        "import static com.cloudbees.groovy.cps.ContinuableTest.StaticMethodHost.methodWithNoArgs; methodWithNoArgs()");
        Continuable c = new Continuable(s);
        Object r = c.run(null);
        assertEquals("hello", r);
    }

    public static class StaticMethodHost {
        public static String methodWithNoArgs() {
            return "hello";
        }
    }

    /**
     * Start running one Continuable, interrupt that and run something else, then come back to it.
     *
     */
    @Test
    public void concatenate() throws Throwable {
        Script s = getCsh().parse("def plus2(i) { return i+2; }\n" + "def i=1;\n"
                + "x = Continuable.suspend('pause1');\n"
                + // this will jump to another script and then come back
                "return plus2(i+x);\n");

        // let the script run to the suspend point
        Continuable c = new Continuable(s);
        assertEquals("pause1", c.run(null));

        Script s2 = getCsh().parse("return 16+Continuable.suspend('pause2')+32;");

        // now create a new Continuable that evaluates s2 first, then come back to where we paused in 'c'
        final Continuable pause = c;
        c = new Continuable(s2, null, new Continuation() {
            @Override
            public Next receive(Object o) {
                // when s2 is done, hand off the value to pause1 to resume execution from there
                return Next.go0(new Outcome((int) o + 64, null), pause);
            }
        });

        // the point of all this trouble is that once the new 'c' is created, the rest of the code
        // doesn't have to know that the new Continuable is a composite of two Continuables.

        assertEquals("pause2", c.run(null));

        // s2 evaluates, then the result goes back to pause1 after +64 adjustment above
        // and the whole thing completes
        Object r = c.run(128);

        assertFalse(c.isResumable()); // it should have been completed

        assertEquals(1 + 2 + 16 + 32 + 64 + 128, r);
    }
}
