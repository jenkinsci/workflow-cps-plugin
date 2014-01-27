package com.cloudbees.groovy.cps;

import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.junit.Assert;
import org.junit.Test;

import static java.util.Arrays.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class BasicTest extends Assert {
    Builder b = new Builder();

    // useful fragment of expressions
    Expression $x = b.getLocalVariable("x");
    Expression $y = b.getLocalVariable("y");
    Expression $z = b.getLocalVariable("z");


    // 3    => 3
    @Test
    public void constant() {
        assertEquals(3, run(b.constant(3)));
    }

    // 1==1, aka ScriptBytecodeAdapter.compareEqual(1,1)    => true
    @Test
    public void onePlusOne() {
        assertEquals(true, run(
                b.staticCall(ScriptBytecodeAdapter.class, "compareEqual",
                    b.constant(1),
                    b.constant(1))));
    }

    // x=1; y=2; x+y    =>  3
    @Test
    public void variable() {
        assertEquals(3, run(
                b.setLocalVariable("x", b.constant(1)),
                b.setLocalVariable("y", b.constant(2)),
                b.plus($x, $y)
        ));
    }

    /*
        sum = 0;
        for (x=0; x<10; x++) {
            sum += x;
        }
        sum     =>      45;
     */
    @Test
    public void forLoop() {
        assertEquals(45, run(
                b.setLocalVariable("sum", b.constant(0)),
                b.forLoop(
                        b.setLocalVariable("x", b.constant(0)),
                        b.lessThan($x, b.constant(10)),
                        b.localVariableAssignOp("x", "plus", b.constant(1)),

                        b.sequence(// for loop body
                                b.localVariableAssignOp("sum", "plus", $x)
                        )),
                b.getLocalVariable("sum")
        ));
    }

    @Test
    public void asyncCallingAsync() {
        class Op {
            public Function add(int x, int y) {
                return new Function(asList("x", "y"),
                        b.sequence(
                            b.setLocalVariable("z",b.functionCall($x,"plus",$y)),
                            b._return($z)
                        ));
            }
        }

        //  z=5; new Op().add(1,2)+z   => 8
        assertEquals(3, run(
                b.setLocalVariable("z", b.constant(0)),     // part of the test is to ensure this 'z' is separated from 'z' in the add function
                b.plus(
                        b.functionCall(b.constant(new Op()), "add", b.constant(1), b.constant(2)),
                        $z)));
    }

    private Object run(Expression... bodies) {
        Next p = new Next(b.sequence(bodies), new Env(null), Continuation.HALT);
        return p.resume().yield;
    }
}
