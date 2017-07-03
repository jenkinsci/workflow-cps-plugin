package com.cloudbees.groovy.cps.sandbox

import com.cloudbees.groovy.cps.*
import com.cloudbees.groovy.cps.impl.FunctionCallEnv
import org.junit.Before
import org.junit.Test
import org.jvnet.hudson.test.Issue;
import org.kohsuke.groovy.sandbox.ClassRecorder

import java.awt.Point

/**
 * @author Kohsuke Kawaguchi
 */
public class SandboxInvokerTest extends AbstractGroovyCpsTest {
    def cr = new ClassRecorder()

    @Override
    protected CpsTransformer createCpsTransformer() {
        new SandboxCpsTransformer()
    }

    @Before public void zeroIota() {
        CpsTransformer.iota.set(0)
    }

    /**
     * Covers all the intercepted operations.
     */
    @Test
    public void basic() {
        evalCpsSandbox("""
            import java.awt.Point;

            def p = new Point(1,3);     // constructor
            assert p.equals(p)          // method call
            assert 4 == p.x+p.y;        // property get
            p.x = 5;                    // property set
            assert 5 == p.@x;           // attribute get
            p.@x = 6;                   // attribute set

            def a = new int[3];
            a[1] = a[0]+7;              // array get & set
            assert a[1]==7;
        """)

        assertIntercept("""
Script1:___cps___0()
Script1:___cps___1()
Script1.super(Script1).setBinding(Binding)
new Point(Integer,Integer)
Point.equals(Point)
Point.x
Point.y
Double.plus(Double)
ScriptBytecodeAdapter:compareEqual(Integer,Double)
Point.x=Integer
Point.@x
ScriptBytecodeAdapter:compareEqual(Integer,Integer)
Point.@x=Integer
int[][Integer]
Integer.plus(Integer)
int[][Integer]=Integer
int[][Integer]
ScriptBytecodeAdapter:compareEqual(Integer,Integer)
"""
)
    }

    @Test
    public void mixtureOfNonTransformation() {
        assert 3==evalCpsSandbox("""
    @NonCPS
    def length(x) {
        return x.length();
    }

    return length("foo")
""")
        assertIntercept('''
Script1:___cps___0()
Script1:___cps___1()
Script1.super(Script1).setBinding(Binding)
Script1.length(String)
String.length()
''')
    }


    private Object evalCpsSandbox(String script) {
        FunctionCallEnv e = Envs.empty();
        e.invoker = new SandboxInvoker();

        cr.register()
        try {
            return parseCps(script).invoke(e, null, Continuation.HALT).run().yield.replay()
        } finally {
            cr.unregister()
        }
    }

    def assertIntercept(String... expected) {
        assertEquals(expected.join("\n").trim(), cr.toString().trim())
    }

    class TrustedCpsCompiler extends AbstractGroovyCpsTest {
    }

    /**
     * Untrusted code -> trusted code -> untrusted code.
     */
    @Test
    public void mixingTrustedAndUntrusted() {
        def trusted = new TrustedCpsCompiler();
        trusted.setUp();

        def untrusted = this;

        untrusted.binding.setVariable("trusted",  trusted.csh.parse("def foo(x) { return [new java.awt.Point(1,x),untrusted.bar()] }"));
        trusted.binding.setVariable("untrusted",untrusted.csh.parse("def bar() { return new File('foo') }"));

        assert [new Point(1,4),new File("foo")]==evalCpsSandbox("trusted.foo(4)");
        assertIntercept("""
Script2:___cps___6()
Script2:___cps___7()
Script2.super(Script2).setBinding(Binding)
Script2.trusted
Script1.foo(Integer)
new File(String)
""")
    }


    public static class Base {
        @Override
        String toString() {
            return "base";
        }
    }
    @Test
    void superClass() {
        assert evalCpsSandbox('''
            class Foo extends SandboxInvokerTest.Base {
                public String toString() {
                    return "x"+super.toString();
                }
            }
            class Bar extends Foo {}
            new Bar().toString();
        ''')=="xbase"

        assertIntercept("""
Script1:___cps___0()
Script1:___cps___1()
Script1.super(Script1).setBinding(Binding)
new Bar()
Foo:___cps___2()
Bar.toString()
Bar.super(Foo).toString()
String.plus(String)
""")
    }

    @Issue("SECURITY-551")
    @Test public void constructors() {
        evalCpsSandbox('''
            import java.awt.Point;
            class C {
                Point p
                C() {
                    p = new Point(1, 3)
                }
            }
            assert new C().p.y == 3
        ''')
        assertIntercept('''
Script1:___cps___0()
Script1:___cps___1()
Script1.super(Script1).setBinding(Binding)
new C()
new Point(Integer,Integer)
C.p=Point
C.p
Point.y
ScriptBytecodeAdapter:compareEqual(Double,Integer)
''')
    }

    @Issue("SECURITY-551")
    @Test public void fields() {
        evalCpsSandbox('''
            import java.awt.Point;
            class C {
                Point p = new Point(1, 3)
            }
            assert new C().p.y == 3
        ''')
        assertIntercept('''
Script1:___cps___0()
Script1:___cps___1()
Script1.super(Script1).setBinding(Binding)
new C()
new Point(Integer,Integer)
C.p
Point.y
ScriptBytecodeAdapter:compareEqual(Double,Integer)
''')
    }

    @Issue("SECURITY-551")
    @Test public void initializers() {
        evalCpsSandbox('''
            import java.awt.Point;
            class C {
                Point p
                {
                    p = new Point(1, 3)
                }
            }
            assert new C().p.y == 3
        ''')
        assertIntercept('''
Script1:___cps___0()
Script1:___cps___1()
Script1.super(Script1).setBinding(Binding)
new C()
new Point(Integer,Integer)
C.p=Point
C.p
Point.y
ScriptBytecodeAdapter:compareEqual(Double,Integer)
''')
    }

}
