package com.cloudbees.groovy.cps

import org.junit.Test

import java.lang.reflect.InvocationTargetException

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class ContinuableTest extends AbstractGroovyCpsTest {
    @Test
    void resumeAndSuspend() {
        def s = csh.parse("""
            int x = 1;
            x = Continuable.suspend(x+1)
            return x+1;
        """)

        def c = new Continuable(s);
        assert c.isResumable()

        def v = c.run(null);
        assert v==2 : "Continuable.suspend(x+1) returns the control back to us";

        def c2 = c.fork()

        for (d in [c,c2]) {
            assert d.isResumable() : "Continuable is resumable because it has 'return x+1' to execute."
            assert d.run(3)==4 : "We resume continuable, then the control comes back from the return statement"

            assert !d.isResumable() : "We've run the program till the end, so it's no longer resumable"
        }
    }

    @Test
    void serializeComplexContinuable() {
        def s = csh.parse("""
            @WorkflowMethod
            def foo(int x) {
                return Continuable.suspend(x);
            }

            @WorkflowMethod
            def plus3(int x) {
                return x+3;
            }

            try {
                for (int x=0; x<1; x++) {
                    while (true) {
                        y = plus3(foo(5))
                        break;
                    }
                }
            } catch (ClassCastException e) {
                y = e;
            }
            return y;
        """)

        def c = new Continuable(s);
        assert c.run(null)==5 : "suspension within a subroutine";

        c = roundtripSerialization(c);  // at this point there's a fairly non-trivial Continuation, so try to serialize it

        assert c.isResumable()
        assert c.run(6)==9;
    }

    @Test
    void howComeBindingIsSerializable() {
        def s = csh.parse("""
                Continuable.suspend(42);
                return value;
""");
        s.setProperty("value",15);
        def c = new Continuable(s);
        assert c.run(null)==42;

        c = roundtripSerialization(c);

        assert c.isResumable()
        assert c.run(null)==15;
    }

    @Test
    void suspend_at_the_end_should_still_count_as_resumable() {
        def s = csh.parse("""
                Continuable.suspend(5);
        """);
        def c = new Continuable(s);
        assert c.run(null)==5;
        assert c.isResumable()
        c.run(null)
        assert !c.isResumable()
    }

    /**
     * Exception not handled in script will be thrown from Continuable.run
     */
    @Test
    void unhandled_exception() {
        def s = csh.parse("""
            throw new ${ContinuableTest.class.name}.HelloException()
        """)
        def c = new Continuable(s)
        try {
            c.run(null)
            fail("should have thrown exception")
        } catch (InvocationTargetException e) {
            assert !c.isResumable()
            assert e.cause instanceof HelloException
        }
    }

    public static class HelloException extends Exception {}

    /**
     * Object passed to {@link Continuable#suspend(Object)} isn't accessible when Continuable
     * resumes, so it shouldn't be a part of the persisted object graph.
     */
    @Test
    void yieldObjectShouldNotBeInObjectGraph() {
        def s = csh.parse("""
                Continuable.suspend(new ContinuableTest.ThisObjectIsNotSerializable());
        """);
        def c = new Continuable(s);
        def r = c.run(null)
        assert r instanceof ThisObjectIsNotSerializable;

        c = roundtripSerialization(c);

        assert c.isResumable()
        assert c.run(42)==42;
    }

    public static class ThisObjectIsNotSerializable {}

    /**
     * Tests {@link Continuable#getStackTrace()}.
     */
    @Test
    void stackTrace() {
        def s = csh.parse("""
            @WorkflowMethod
            def x(i,v) {
              if (i>0)
                y(i-1,v);       // line 5
              else
                Continuable.suspend(v); // line 7
            }
            @WorkflowMethod
            def y(i,v) {
              if (i>0)
                x(i-1,v);   // line 12
              else
                Continuable.suspend(v); // line 14
            }

            x(5,3); // line 17
        """)

        def c = new Continuable(s);

        assert c.stackTrace.isEmpty()

        def v = c.run(null);
        assert v==3

        assert c.stackTrace.join("\n")=="""
Script1.y(Script1.groovy:14)
Script1.x(Script1.groovy:5)
Script1.y(Script1.groovy:12)
Script1.x(Script1.groovy:5)
Script1.y(Script1.groovy:12)
Script1.x(Script1.groovy:5)
Script1.run(Script1.groovy:17)
""".trim()
    }

}
