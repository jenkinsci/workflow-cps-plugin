package com.cloudbees.groovy.cps;

import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class TestProgram extends Assert {
    Builder b = new Builder();

    // useful fragment of expressions
    Expression $x = b.getLocalVariable("x");
    Expression $y = b.getLocalVariable("y");


    @Test
    public void constant() {
        // constant 3 should evaluate to '3'
        assertEquals(3, run(b.constant(3)));
    }

    @Test
    public void onePlusOne() {
        // 1==1, aka ScriptBytecodeAdapter.compareEqual(1,1)
        assertEquals(true, run(
                b.staticCall(ScriptBytecodeAdapter.class, "compareEqual",
                    b.constant(1),
                    b.constant(1))));
    }

    @Test
    public void variable() {
        // x=1; y=2; x+y    =>  3
        assertEquals(3, run(
                b.setLocalVariable("x", b.constant(1)),
                b.setLocalVariable("y", b.constant(2)),
                b.plus($x, $y)
        ));
    }

    @Test
    public void forLoop() {
        /*
            sum = 0;
            for (x=0; x<10; x++) {
                sum += x;
            }
            sum     =>      55;
         */
        assertEquals(45, run(
                b.setLocalVariable("sum", b.constant(0)),
                b._for(
                        b.setLocalVariable("x",b.constant(0)),
                        b.lessThan($x,b.constant(10)),
                        b.localVariableAssignOp("x", "plus", b.constant(1)),

                        b.sequence(// for loop body
                            b.localVariableAssignOp("sum", "plus", $x)
                        )),
                b.getLocalVariable("sum")
        ));

//        b.sequence(
//            b.setLocalVariable("sum", b.constant(0)),
//            b._for( b.setLocalVariable("x",b.constant(1)), null, )
//

    }

    private Object run(Expression... bodies) {
        Next p = new Next(b.sequence(bodies), new EnvImpl(null), Continuation.HALT);
        return p.resume().yield;
    }
}
