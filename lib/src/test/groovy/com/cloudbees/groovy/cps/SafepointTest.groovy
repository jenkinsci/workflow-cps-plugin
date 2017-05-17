package com.cloudbees.groovy.cps

import groovy.transform.NotYetImplemented
import org.junit.Test

/**
 * Make sure that a safepoint covers all the infinite loops.
 *
 * @author Kohsuke Kawaguchi
 */
class SafepointTest extends AbstractGroovyCpsTest {
    @Override
    protected CpsTransformer createCpsTransformer() {
        def t = super.createCpsTransformer()
        t.configuration = new TransformerConfiguration().withSafepoint(SafepointTest.class,"safepoint")
        return t
    }

    @Test
    void whileLoop() {
        assert evalCPSonly('''
            while (true) ;
        ''') == SAFEPOINT
    }

    @NotYetImplemented // Groovy doesn't support do-while
    @Test
    void doWhileLoop() {
        assert evalCPSonly('''
            do {} while (true) ;
        ''') == SAFEPOINT
    }

    @Test
    void forLoop() {
        assert evalCPSonly('''
            for (;;) {}
        ''') == SAFEPOINT
    }

    @Test
    void recursion() {
        assert evalCPSonly('''
            def foo() { foo(); }
            foo()
        ''') == SAFEPOINT
    }

    @Test
    void closure() {
        assert evalCPSonly('''
            def x = { -> }
            x()
        ''') == SAFEPOINT
    }

    /**
     * Gets invoked at the safepoint.
     */
    public static void safepoint() {
        Continuable.suspend(SAFEPOINT)
    }

    private static final Object SAFEPOINT = "Yo!"
}
