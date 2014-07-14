package com.cloudbees.groovy.cps.impl

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest
import com.cloudbees.groovy.cps.Continuable
import com.cloudbees.groovy.cps.Continuation
import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class PropertyAccessBlockTest extends AbstractGroovyCpsTest {
    @Test
    void asyncExecutionOfPropertyGet() {
        def inv = parseCps("""
            class Foo {
                Object getAlpha() {
                    return Continuable.suspend('suspended');
                }
            }
            return new Foo().alpha;
        """)

        def c = new Continuable(inv.invoke(null, null, Continuation.HALT))
        assert 'suspended'==c.run(null) // should have suspended
        assert 5 == c.run(5);   // when resume, the getter should return
    }

    @Test
    void asyncExecutionOfPropertySet() {
        def inv = parseCps("""
            class Foo {
                private int x = 3;
                void setAlpha(int x) {
                    this.x = Continuable.suspend(x);
                }
                int getAlpha() {
                    return x;
                }
            }
            def f = new Foo()
            f.alpha += 7;
            return f.alpha;
        """)

        def c = new Continuable(inv.invoke(null, null, Continuation.HALT))
        assert 10==c.run(null)      // should have suspended
        assert 13 == c.run(13);     // when resume, we should see that as the final value.
    }
}
