package com.cloudbees.groovy.cps.impl;

import static org.junit.Assert.assertEquals;

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import org.junit.Test;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class PropertyAccessBlockTest extends AbstractGroovyCpsTest {
    @Test
    public void asyncExecutionOfPropertyGet() throws Throwable {
        CpsCallableInvocation inv = parseCps("class Foo {\n" + "    Object getAlpha() {\n"
                + "        return Continuable.suspend('suspended')\n"
                + "    }\n"
                + "}\n"
                + "return new Foo().alpha\n");

        Continuable c = new Continuable(inv.invoke(null, null, Continuation.HALT));
        assertEquals("suspended", c.run(null)); // should have suspended
        assertEquals(5, c.run(5)); // when resume, the getter should return
    }

    @Test
    public void asyncExecutionOfPropertySet() throws Throwable {
        CpsCallableInvocation inv = parseCps("class Foo {\n" + "    private int x = 3\n"
                + "    void setAlpha(int x) {\n"
                + "        this.x = Continuable.suspend(x)\n"
                + "    }\n"
                + "    int getAlpha() {\n"
                + "        return x\n"
                + "    }\n"
                + "}\n"
                + "def f = new Foo()\n"
                + "f.alpha += 7\n"
                + "return f.alpha\n");

        Continuable c = new Continuable(inv.invoke(null, null, Continuation.HALT));
        assertEquals(10, c.run(null)); // should have suspended
        assertEquals(13, c.run(13)); // when resume, we should see that as the final value.
    }
}
