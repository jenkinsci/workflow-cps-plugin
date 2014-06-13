package com.cloudbees.groovy.cps

import com.cloudbees.groovy.cps.impl.CpsCallableInvocation
import org.junit.Ignore
import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class CpsTransformerTest extends AbstractGroovyCpsTest {
    @Test
    void helloWorld() {
        assert evalCPS("'hello world'.length()")==11
    }

    @Test
    void comparison() {
        for(int i in [1,2,3]) {
            for (int j in [1,2,3]) {
                assert evalCPS("${i} < ${j}") == (i<j);
                assert evalCPS("${i} <= ${j}")== (i<=j);
                assert evalCPS("${i} > ${j}") == (i>j);
                assert evalCPS("${i} >= ${j}")== (i>=j);
            }
        }
    }

    @Test
    void forInLoop() {
        assert evalCPS("x=0; for (i in [1,2,3,4,5]) x+=i; return x;")==15;
    }

    @Test
    void variableAssignment() {
        assert evalCPS("x=3; x+=2; return x;")==5;
    }

    @Test
    void localVariable() {
        assert evalCPS("int x=3; x+=2; return x;")==5;
    }

    @Test
    void increment() {
        assert evalCPS("""
            x=0;
            y = x++;
            z = ++x;
            return x+"."+y+"."+z;
        """)=="2.0.2";
    }

    @Test
    void decrement() {
        assert evalCPS("""
            x=5;
            y = x--;
            z = --x;
            return x+"."+y+"."+z;
        """)=="3.5.3";
    }

    @Test
    void break_() {
        assert evalCPS("""
            x=0;
            int i=0;
            for (i=0; i<5; i+=1) {
                break;
                x+=1;
            }
            return i+x;
        """)==0;
    }

    @Test
    void globalBreak_() {
        assert evalCPS("""
            x=0;
            int i=0;
            int j=0;

            I:
            for (i=0; i<5; i+=1) {
                J:
                for (j=0; j<5; j+=1) {
                  break I;
                  x+=1;
                }
                x+=1;
            }
            return i+"."+j+"."+x;
        """)=="0.0.0";
    }

    @Test
    void functionCall() {
        assert evalCPS("""
            int i=1;
            i.plus(2)
        """)==3;
    }

    @Test
    void functionCall0arg() {
        assert evalCPS("""
            123.toString()
        """)=="123";
    }

    @Test
    void constructorCall() {
        assert evalCPS("""
            new String("abc"+"def")
        """)=="abcdef";
    }

    @Test
    void constructorCall0arg() {
        assert evalCPS("""
            new String()
        """)=="";
    }

    @Test
    void workflowCallingWorkflow() {
        assert evalCPS("""
            def fib(int x) {
              if (x==0)     return 0;
              if (x==1)     return 1;
              x = fib(x-1)+fib(x-2);    // assignment to make sure x is treated as local variable
              return x;
            }
            fib(10);
        """)==55
    }

    /**
     *
     */
    @Test
    void exceptionFromNonCpsCodeShouldBeCaughtByCatchBlockInCpsCode() {
        assert evalCPS("""
            def foo() {
              "abc".substring(5); // will caught exception
              return "fail";
            }

            try {
              return foo();
            } catch(StringIndexOutOfBoundsException e) {
              return e.message;
            }
        """)=="String index out of range: -2"
    }

    /**
     * while loop that evaluates to false and doesn't go through the body
     */
    @Test
    void whileLoop() {
        assert evalCPS("""
            int x=1;
            while (false) {
                x++;
            }
            return x;
        """)==1
    }

    /**
     * while loop that goes through several iterations.
     */
    @Test
    void whileLoop5() {
        assert evalCPS("""
            int x=1;
            while (x<5) {
                x++;
            }
            return x;
        """)==5
    }

    /**
     * do-while loop that evaluates to false immediately
     */
    @Test
    @Ignore
    void doWhileLoop() {
        assert evalCPS("""
            int x=1;
            do {
                x++;
            } while (false);
            return x;
        """)==2
    }

    /**
     * do/while loop that goes through several iterations.
     */
    @Test
    @Ignore
    void dowhileLoop5() {
        assert evalCPS("""
            int x=1;
            do {
                x++;
            } while (x<5);
            return x;
        """)==5
    }

    @Test
    void helloClosure() {
        assert evalCPS("""
            x = { -> 5 }
            return x();
        """)==5
    }

    @Test
    void closureShouldCaptureLiveVariables() {
        assert evalCPS("""
            def c1,c2;

            { ->
                def x = 0;
                c1 = { return x; }
                c2 = { v -> x=v; }
            }();

            r = ""+c1();
            c2(3);
            r += "."+c1();
            c2(5);
            r += "."+c1();

            return r;
        """)=="0.3.5"
    }

    @Test
    void closureHasImplicitItVariable() {
        assert evalCPS("""
            c = { it+1 }

            c(3);
        """)==4
    }

    @Test
    void serialization() {
        CpsCallableInvocation s = parseCps("""
            def plus3(int x) {
                return x+3;
            }

            for (int x=0; x<10; x++) {// meaningless code to cram as much coding construct as possible
                try {
                    while (false)
                        ;
                } catch(Exception e) {
                    ;
                }
            }
            1+plus3(3*2)
        """)
        def cx = new Continuable(s.invoke(null, null, Continuation.HALT))
        cx = roundtripSerialization(cx)
        assert 10==cx.run(null)
    }

    @Test
    void assertion() {
        // when assertion passes
        assert evalCPS("""
            assert true
            assert true : "message"
            return 3;
        """)==3

        try {
            evalCPS("""
                assert 1+2 == ((4));
            """)
            fail();
        } catch (AssertionError e) {
            assert e.message.contains("1+2 == ((4))")
        }

        try {
            evalCPS("""
                assert (1+2) == 4 : "with message";
            """)
            fail();
        } catch (AssertionError e) {
            assert e.message=="with message. Expression: assert (1+2) == 4 : \"with message\""
        }
    }

    @Test
    void unaryOps() {
        assert evalCPS("""
            def x = 5;
            def y = -x;
            def z = +x;

            return y+z;
""")==0;
    }

    @Test
    void not() {
        assert evalCPS("""
            def x = true;
            def y = !x;
            def z = !y;

            return "y="+y+",z="+z;
""")=="y=false,z=true";
    }

    @Test
    void bitwiseNegative() {
        assert evalCPS("""
            int x = 32;
            return ~x;
""")==-33;
    }

    @Test
    void gstring() {
        assert evalCPS('''
            def x = "foo";
            return "hello ${1+3}=${x}";
''')=="hello 4=foo";
    }

    @Test
    void ternaryOp() {
        assert evalCPS('''
            return true ? 5 : null.makeCall();
''')==5;
    }

    @Test
    void ternaryOp2() {
        assert evalCPS("false ? bogus.noSuchCall() : 'zot'")=='zot';
    }

    @Test
    void elvisOp() {
        assert evalCPS("def x=0; return ++x ?: -1")==1;
        assert evalCPS("def x=0; return x++ ?: -1")==-1;
    }

    @Test
    void instanceOf() {
        assert evalCPS("null instanceof String")==false;
        assert evalCPS("3 instanceof Integer")==true;
        assert evalCPS("new RuntimeException() instanceof Exception")==true;
        assert evalCPS("'12345' instanceof String")==true;
    }

    @Test
    void compoundBitwiseAssignment() {
        [0,1,2,3,4].each { x->
            [0,1,2,3,4].each { y ->
                assert evalCPS("def x=${x}; x&=${y}; return x;")== (x&y);
                assert evalCPS("def x=${x}; x|=${y}; return x;")== (x|y);
                assert evalCPS("def x=${x}; x^=${y}; return x;")== (x^y);
            }
        }
    }
}
