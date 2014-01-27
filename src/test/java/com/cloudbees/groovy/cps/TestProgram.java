package com.cloudbees.groovy.cps;

import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class TestProgram extends Assert {
    Builder b = new Builder();


    /**
     *
     */
    @Test
    public void test1() {
        Expression e = b.staticCall(ScriptBytecodeAdapter.class, "compareEqual",
                b.constant(1),
                b.constant(1));
        assertEquals(true,run(e));

        /*
            sum = 0;
            for (x=0; x<10; x++) {
                sum += x;
            }
            println sum;
         */
//        b.sequence(
//            b.setLocalVariable("sum", b.constant(0)),
//            b._for( b.setLocalVariable("x",b.constant(1)), null, )
//

    }

    private Object run(Expression e) {
        Next p = new Next(e, new EnvImpl(null), Continuation.HALT);
        return p.resume().yield;
    }
}
