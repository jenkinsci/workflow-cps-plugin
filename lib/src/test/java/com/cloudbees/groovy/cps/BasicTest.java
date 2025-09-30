package com.cloudbees.groovy.cps;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.CpsFunction;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.junit.Test;

/**
 * @author Kohsuke Kawaguchi
 */
public class BasicTest {
    Builder b = new Builder(MethodLocation.UNKNOWN);

    // useful fragment of expressions
    Block $x = b.localVariable("x");
    Block $y = b.localVariable("y");
    Block $z = b.localVariable("z");

    /**
     * Evaluates the given body and return the yielded value.
     */
    private Object run(Block... bodies) {
        try {
            Env e = Envs.empty();
            Next p = new Next(b.block(bodies), e, Continuation.HALT);
            return p.run().yield.wrapReplay();
        } catch (InvocationTargetException x) {
            throw new AssertionError(x);
        }
    }

    // 3    => 3
    @Test
    public void constant() {
        assertEquals(3, run(b.constant(3)));
    }

    // 1==1, aka ScriptBytecodeAdapter.compareEqual(1,1)    => true
    @Test
    public void onePlusOne() {
        assertEquals(true, run(b.staticCall(0, ScriptBytecodeAdapter.class, "compareEqual", b.one(), b.one())));
    }

    // x=1; y=2; x+y    =>  3
    @Test
    public void variable() {
        assertEquals(
                3, run(b.setLocalVariable(0, "x", b.one()), b.setLocalVariable(0, "y", b.two()), b.plus(0, $x, $y)));
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
        assertEquals(
                45,
                run(
                        b.setLocalVariable(0, "sum", b.zero()),
                        b.forLoop(
                                null,
                                b.setLocalVariable(0, "x", b.zero()),
                                b.lessThan(0, $x, b.constant(10)),
                                b.localVariableAssignOp(0, "x", "plus", b.one()),
                                b.block( // for loop body
                                        b.localVariableAssignOp(0, "sum", "plus", $x))),
                        b.localVariable("sum")));
    }

    /**
     * Makes sure the return statement prevents the rest of the code from executing.
     *
     * x=0; return x; x+=1;     => 0
     */
    @Test
    public void returnStatement() {
        assertEquals(
                0,
                run(
                        b.setLocalVariable(0, "x", b.zero()),
                        b.return_($x),
                        b.localVariableAssignOp(0, "x", "plus", b.one()),
                        b.plus(0, $x, $y)));
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
        assertEquals(
                expected,
                run(
                        b.setLocalVariable(0, "x", b.zero()),
                        b.if_(
                                b.constant(cond),
                                b.setLocalVariable(0, "x", b.one()),
                                b.setLocalVariable(0, "x", b.two())),
                        $x));
    }

    /**
     * A CPS function calling another CPS function.
     */
    @Test
    public void asyncCallingAsync() {
        class Op {
            public int add(int x, int y) {
                CpsFunction f = new CpsFunction(
                        List.of("x", "y"),
                        b.sequence(b.setLocalVariable(0, "z", b.functionCall(0, $x, "plus", $y)), b.return_($z)));
                throw new CpsCallableInvocation(f, this, x, y);
            }
        }

        //  z=5; new Op().add(1,2)+z   => 8
        assertEquals(
                3,
                run(
                        b.setLocalVariable(
                                0, "z",
                                b.zero()), // part of the test is to ensure this 'z' is separated from 'z' in the
                        // add function
                        b.plus(0, b.functionCall(0, b.constant(new Op()), "add", b.one(), b.two()), $z)));
    }

    public static class InstantiationTest {
        int v;

        public InstantiationTest(int x, int y) {
            v = x + y;
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

        v = (InstantiationTest) run(b.new_(0, InstantiationTest.class, b.constant(3)));
        assertEquals(3, v.v);

        v = (InstantiationTest) run(b.new_(0, InstantiationTest.class, b.constant(3), b.constant(4)));
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
        assertEquals(
                "foo",
                run(b.tryCatch(
                        b.sequence(
                                b.throw_(0, b.new_(0, RuntimeException.class, b.constant("foo"))),
                                b.return_(b.null_())),
                        null,
                        new CatchExpression(
                                Exception.class,
                                "e",
                                b.block(b.return_(b.functionCall(0, b.localVariable("e"), "getMessage")))))));
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
            public void throw_(int depth, String message) {
                Block $depth = b.localVariable("depth");
                CpsFunction f = new CpsFunction(
                        List.of("depth", "message"),
                        b.block(b.if_(
                                b.lessThan(0, b.zero(), $depth),
                                b.functionCall(
                                        0,
                                        b.this_(),
                                        "throw_",
                                        b.minus(0, $depth, b.one()),
                                        b.localVariable("message")),
                                // else
                                b.throw_(0, b.new_(0, IllegalArgumentException.class, b.localVariable("message"))))));
                throw new CpsCallableInvocation(f, this, depth, message);
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
        assertEquals(
                "hello1",
                run(
                        b.setLocalVariable(
                                0, "x",
                                b.zero()), // part of the test is to ensure this 'z' is separated from 'z' in the
                        // add function
                        b.tryCatch(
                                b.block(
                                        b.setLocalVariable(0, "x", b.one()),
                                        b.functionCall(
                                                0, b.constant(new Op()), "throw_", b.constant(3), b.constant("hello")),
                                        b.setLocalVariable(0, "x", b.two())),
                                null,
                                new CatchExpression(
                                        Exception.class,
                                        "e",
                                        b.return_(b.plus(
                                                0,
                                                b.property(0, b.localVariable("e"), "message"),
                                                b.localVariable("x")))))));
    }

    /**
     * x = new Exception("foo");
     * new String(x.message.bytes)      =>  "foo"
     */
    @Test
    public void propertyGetAccess() {
        assertEquals(
                "foo",
                run(
                        b.setLocalVariable(0, "x", b.new_(0, Exception.class, b.constant("foo"))),
                        b.new_(0, String.class, b.property(0, b.property(0, $x, "message"), "bytes"))));
    }

    public static class PropertyTest {
        private int x = 0;

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int y = 0;
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
                b.setLocalVariable(0, "x", b.constant(p)),
                b.setProperty(0, $x, "x", b.one()),
                b.setProperty(0, $x, "y", b.two()));
        assertEquals(p.x, 1);
        assertEquals(p.y, 2);
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
        assertEquals(
                0,
                run(
                        b.if_(
                                b.true_(),
                                b.sequence(b.declareVariable(int.class, "x"), b.setLocalVariable(0, "x", b.one()))),
                        b.declareVariable(int.class, "x"),
                        b.return_($x)));
    }
}
