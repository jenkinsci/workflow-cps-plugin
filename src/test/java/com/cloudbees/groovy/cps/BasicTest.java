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

    /**
     * Makes sure the return statement prevents the rest of the code from executing.
     *
     * x=0; return x; x+=1;     => 0
     */
    @Test
    public void returnStatement() {
        assertEquals(0, run(
                b.setLocalVariable("x", b.constant(0)),
                b.return_($x),
                b.localVariableAssignOp("x", "plus", b.constant(1)),
                b.plus($x, $y)
        ));
    }

    /**
     * x = 0;
     * if (true) {
     *     x = 1;
     * } else {
     *     x = 2;
     * }
     * x        => 1
     */
    @Test
    public void ifTrue() {
        if_(true, 1);
        if_(false, 2);
    }

    private void if_(boolean cond, int expected) {
        assertEquals(expected, run(
                b.setLocalVariable("x", b.constant(0)),
                b.if_( b.constant(cond),
                        b.setLocalVariable("x",b.constant(1)),
                        b.setLocalVariable("x",b.constant(2))),
                $x
        ));
    }

    @Test
    public void asyncCallingAsync() {
        class Op {
            public Function add(int x, int y) {
                return new Function(asList("x", "y"),
                        b.sequence(
                            b.setLocalVariable("z",b.functionCall($x,"plus",$y)),
                            b.return_($z)
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

    public static class InstantiationTest {
        int v;
        public InstantiationTest(int x, int y) {
            v = x+y;
        }
        public InstantiationTest(int x) {
            v = x;
        }
    }

    /**
     * new InstantiationTest(3)     => ...
     * new InstantiationTest(3,4)   => ...
     */
    @Test
    public void newInstance() {
        InstantiationTest v;

        v = run(b.new_(InstantiationTest.class, b.constant(3)));
        assertEquals(3, v.v);

        v = run(b.new_(InstantiationTest.class, b.constant(3), b.constant(4)));
        assertEquals(7, v.v);
    }

    /**
     * try {
     *     throw new RuntimeException("foo");
     *     return null;
     * } catch (Exception e) {
     *     return e.getMessage();
     * }
     */
    @Test
    public void localExceptionHandling() {
        assertEquals("foo",run(
                b.tryCatch(
                        b.sequence(
                                b.throw_(b.new_(RuntimeException.class, b.constant("foo"))),
                                b.return_(b.null_())
                        ),

                        new CatchExpression(Exception.class, "e", b.sequence(
                            b.return_(b.functionCall(b.getLocalVariable("e"),"getMessage"))
                        ))
                )
        ));
    }

    private <T> T run(Expression... bodies) {
        Env e = new FunctionCallEnv(null,null,Continuation.HALT);
        Next p = new Next(b.sequence(bodies), e, Continuation.HALT);
        return (T)p.resume().yield;
    }
}
