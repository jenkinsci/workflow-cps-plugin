package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.FunctionCallEnv;
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
    Block $x = b.localVariable("x");
    Block $y = b.localVariable("y");
    Block $z = b.localVariable("z");


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
                    b.one(),
                    b.one())));
    }

    // x=1; y=2; x+y    =>  3
    @Test
    public void variable() {
        assertEquals(3, run(
                b.setLocalVariable("x", b.one()),
                b.setLocalVariable("y", b.two()),
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
                b.setLocalVariable("sum", b.zero()),
                b.forLoop(
                        b.setLocalVariable("x", b.zero()),
                        b.lessThan($x, b.constant(10)),
                        b.localVariableAssignOp("x", "plus", b.one()),

                        b.sequence(// for loop body
                                b.localVariableAssignOp("sum", "plus", $x)
                        )),
                b.localVariable("sum")
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
                b.setLocalVariable("x", b.zero()),
                b.return_($x),
                b.localVariableAssignOp("x", "plus", b.one()),
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
                b.setLocalVariable("x", b.zero()),
                b.if_( b.constant(cond),
                        b.setLocalVariable("x",b.one()),
                        b.setLocalVariable("x",b.two())),
                $x
        ));
    }

    /**
     * A CPS function calling another CPS function.
     */
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
                b.setLocalVariable("z", b.zero()),     // part of the test is to ensure this 'z' is separated from 'z' in the add function
                b.plus(
                        b.functionCall(b.constant(new Op()), "add", b.one(), b.two()),
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
                            b.return_(b.functionCall(b.localVariable("e"),"getMessage"))
                        ))
                )
        ));
    }

    /**
     * An exception thrown in a function caught by another function
     */
    @Test
    public void exceptionStackUnwinding() {
        class Op {
            /**
             * if (0<depth)
             *      throw_(depth-1,message);
             * else
             *      throw new IllegalArgumentException(message)
             */
            public Function throw_(int depth, String message) {
                Block $depth = b.localVariable("depth");
                return new Function(asList("depth", "message"),
                        b.sequence(
                            b.if_(b.lessThan(b.zero(), $depth),
                                    b.functionCall(b.this_(), "throw_", b.minus($depth,b.one()), b.localVariable("message")),
                                // else
                                    b.throw_(b.new_(IllegalArgumentException.class, b.localVariable("message")))
                            )
                        ));
            }
        }

        /*
            int x;
            try {
                x = 1;
                new Op().throw_(3,"hello")
                x = 2; // make sure this line gets skipped
            } catch (Exception e) {
                return e.message + x;
            }
         */
        assertEquals("hello1", run(
                b.setLocalVariable("x", b.zero()),     // part of the test is to ensure this 'z' is separated from 'z' in the add function
                b.tryCatch(
                        b.sequence(
                                b.setLocalVariable("x", b.one()),
                                b.functionCall(b.constant(new Op()), "throw_", b.constant(3), b.constant("hello")),
                                b.setLocalVariable("x", b.two())
                        ),
                        new CatchExpression(Exception.class, "e",
                                b.return_(b.plus(
                                        b.property(b.localVariable("e"), "message"),
                                        b.localVariable("x"))))
                )));
    }

    /**
     * x = new Exception("foo");
     * new String(x.message.bytes)      =>  "foo"
     */
    @Test
    public void propertyGetAccess() {
        assertEquals("foo",run(
                b.setLocalVariable("x", b.new_(Exception.class, b.constant("foo"))),
                b.new_(String.class, b.property(b.property($x, "message"), "bytes"))
        ));
    }

    public static class PropertyTest {
        private int x=0;

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int y=0;
    }

    /**
     * x = new PropertyTest();
     * x.x = 1;
     * x.y = 2;
     */
    @Test
    public void propertySetAccess() {
        PropertyTest p = new PropertyTest();
        run(
                b.setLocalVariable("x", b.constant(p)),
                b.setProperty($x,"x", b.one()),
                b.setProperty($x,"y", b.two())
        );
        assertEquals(p.x,1);
        assertEquals(p.y,2);
    }

    /**
     * return null;
     */
    @Test
    public void yieldNull() {
        assertNull(run(b.return_(b.null_())));
    }

    /**
     * if (true) {
     *     int x = 1;
     * }
     * int x;
     * return x;
     */
    @Test
    public void blockScopedVariable() {
        assertEquals(0,run(
                b.if_(b.true_(), b.sequence(
                        b.declareVariable(int.class,"x"),
                        b.setLocalVariable("x", b.one())
                )),
                b.declareVariable(int.class,"x"),
                b.return_($x)
        ));
        // TODO: variable has to have a type for initialization
    }

    private <T> T run(Block... bodies) {
        Env e = new FunctionCallEnv(null,null,Continuation.HALT);
        Next p = new Next(b.sequence(bodies), e, Continuation.HALT);
        return (T)p.resume().yieldedValue();
    }
}
