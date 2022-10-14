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

        assert c.isResumable() : "Continuable is resumable because it has 'return x+1' to execute."
        assert c.run(3)==4 : "We resume continuable, then the control comes back from the return statement"

        assert !c.isResumable() : "We've run the program till the end, so it's no longer resumable"
    }

    @Test
    void fork() {
        def s = csh.parse("""\n\
            def addOne(def x) { x + 1 };
            int x = 1;
            x = addOne(Continuable.suspend(x+1))
            return x+1;
        """)

        def c = new Continuable(s);
        assert c.isResumable()

        def v = c.run(null);
        assert v==2 : "Continuable.suspend(x+1) returns the control back to us";

        def c2 = c.fork()

        assert c.isResumable() : "Continuable is resumable because it has 'return x+1' to execute."
        assert c.run(2)==4 : "We resume continuable, then the control comes back from the return statement"
        assert !c.isResumable() : "We've run the program till the end, so it's no longer resumable"

        assert c2.isResumable() : "Continuable is resumable because it has 'return x+1' to execute."
        try {
            assert c2.run(2)==4 : "We resume continuable, then the control comes back from the return statement"
            fail("should have thrown exception");
        } catch (ArrayIndexOutOfBoundsException e) {
            // Since c2 is a shallow clone, the FunctionCallBlock for addOne(Continuable.suspend(x+1)) has already
            // evaluated its arguments, so when we try to run it again, we get an error.
        }
    }

    @Test
    void serializeComplexContinuable() {
        def s = csh.parse("""
            def foo(int x) {
                return Continuable.suspend(x);
            }

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

            def x(i,v) {
              if (i>0)
                y(i-1,v);       // line 5
              else
                Continuable.suspend(v); // line 7
            }
            
            def y(i,v) {
              if (i>0)
                x(i-1,v);   // line 12
              else
                Continuable.suspend(v); // line 14
            }

            x(5,3); // line 17
        """)

        def c = new Continuable(s);

        // stack trace is empty if it hasn't been started
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

        c.run(null)

        // stack trace is empty if there's nothing more to execute
        assert c.stackTrace.isEmpty()
    }

    /**
     * Triggers the use of {@link org.codehaus.groovy.ast.expr.StaticMethodCallExpression}
     */
    @Test
    public void staticMethod1() {
        def s = csh.parse("import static java.lang.Class.forName; forName('java.lang.Integer')")
        def c = new Continuable(s);
        def r = c.run(null)
        assert r==Integer.class
    }

    /**
     * Static method call expression with two arguments
     */
    @Test
    public void staticMethod2() {
        def s = csh.parse("import static java.lang.Integer.toString; toString(31,16)")
        def c = new Continuable(s);
        def r = c.run(null)
        assert r=="1f"
    }

    /**
     * Static method call expression with no arguments
     */
    @Test
    public void staticMethod0() {
        def s = csh.parse("import static com.cloudbees.groovy.cps.ContinuableTest.StaticMethodHost.methodWithNoArgs; methodWithNoArgs()")
        def c = new Continuable(s);
        def r = c.run(null)
        assert r=="hello"
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
    public void concatenate() {
        def s = csh.parse("""
            def plus2(i) { return i+2; }

            def i=1;
            x = Continuable.suspend("pause1"); // this will jump to another script and then come back
            return plus2(i+x);
        """)

        // let the script run to the suspend point
        def c = new Continuable(s);
        assert c.run(null)=="pause1";

        def s2 = csh.parse("""
            return 16+Continuable.suspend("pause2")+32;
        """)

        // now create a new Continuable that evaluates s2 first, then come back to where we paused in 'c'
        c = new Continuable(s2,null,new Continuation() {
            final Continuable pause = c;
            @Override
            Next receive(Object o) {
                // when s2 is done, hand off the value to pause1 to resume execution from there
                return Next.go0(new Outcome(o+64,null),pause);
            }
        });

        // the point of all this trouble is that once the new 'c' is created, the rest of the code
        // doesn't have to know that the new Continuable is a composite of two Continuables.



        assert c.run(null)=="pause2";

        // s2 evaluates, then the result goes back to pause1 after +64 adjustment above
        // and the whole thing completes
        def r = c.run(128);

        assert !c.isResumable();    // it should have been completed

        assert r == 1+2 +16+32 +64+128;
    }


    @Test
    public void superInterrupt() {
        def s = csh.parse("""
            def infiniteLoop() {
                while (true)
                    ; // infinite loop
            }
            infiniteLoop(); // line 6
        """);

        def ex = new RuntimeException("Yippie!");

        def c= new Continuable(s);
        new Thread({ ->
            Thread.sleep(100);
            c.superInterrupt(ex);
        }).start();

        def o = c.run0(new Outcome(null,null));
        assert o.abnormal==ex;

        StringWriter sw = new StringWriter();
        o.abnormal.printStackTrace(new PrintWriter(sw));
        // TODO: right now we cannot capture the exact location of the execution that the exception was thrown from.
        // see Continuable.run0 for details
//        assert sw.toString().contains("Script1.infiniteLoop");
        assert sw.toString().contains("Script1.run(Script1.groovy:6)");
    }
}
