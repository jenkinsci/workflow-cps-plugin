package com.cloudbees.groovy.cps

import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class ContinuableTest extends AbstractGroovyCpsTest {
    @Test
    void resumeAndSuspend() {
        def inv = parseCps("""
            int x = 1;
            x = Continuable.suspend(x+1)
            return x+1;
        """)

        def c = new Continuable(inv.invoke(null,Continuation.HALT));
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
        def inv = parseCps("""
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

        def c = new Continuable(inv.invoke(null,Continuation.HALT));
        assert c.run(null)==5 : "suspension within a subroutine";

        c = roundtripSerialization(c);  // at this point there's a fairly non-trivial Continuation, so try to serialize it

        assert c.isResumable()
        assert c.run(6)==9;
    }
}
