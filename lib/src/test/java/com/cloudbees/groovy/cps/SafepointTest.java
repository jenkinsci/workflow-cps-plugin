package com.cloudbees.groovy.cps;

import static org.junit.Assert.assertEquals;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Make sure that a safepoint covers all the infinite loops.
 *
 * @author Kohsuke Kawaguchi
 */
public class SafepointTest extends AbstractGroovyCpsTest {
    @Override
    protected CpsTransformer createCpsTransformer() {
        CpsTransformer t = super.createCpsTransformer();
        t.setConfiguration(new TransformerConfiguration().withSafepoint(SafepointTest.class, "safepoint"));
        return t;
    }

    @Test
    public void whileLoop() throws Throwable {
        assertEquals(SAFEPOINT, evalCPSonly("while (true) ;"));
    }

    @Ignore("Groovy 2.x doesn't support do-while loops")
    @Test
    public void doWhileLoop() throws Throwable {
        assertEquals(SAFEPOINT, evalCPSonly("do { } while (true) ;"));
    }

    @Test
    public void forLoop() throws Throwable {
        assertEquals(SAFEPOINT, evalCPSonly("for (;;) { }"));
    }

    @Test
    public void recursion() throws Throwable {
        assertEquals(SAFEPOINT, evalCPSonly("def foo() { foo() }; foo();"));
    }

    @Test
    public void closure() throws Throwable {
        assertEquals(SAFEPOINT, evalCPSonly("def x = { -> }; x();"));
    }

    /**
     * Gets invoked at the safepoint.
     */
    public static void safepoint() {
        Continuable.suspend(SAFEPOINT);
    }

    private static final Object SAFEPOINT = "Yo!";
}
