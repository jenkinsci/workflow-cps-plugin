package com.cloudbees.groovy.cps.sandbox

import com.cloudbees.groovy.cps.*
import com.cloudbees.groovy.cps.impl.FunctionCallEnv
import org.codehaus.groovy.runtime.ProxyGeneratorAdapter
import org.junit.Before
import org.junit.Test
import org.jvnet.hudson.test.Issue
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

    @Issue("JENKINS-46088")
    @Test
    void matcherTypeAssignment() {
        assert "falsefalse" == evalCpsSandbox('''
    @NonCPS
    def nonCPSMatcherMethod(String x) {
       java.util.regex.Matcher m = x =~ /bla/
       return m.matches()
    }
    
    def cpsMatcherMethod(String x) {
        java.util.regex.Matcher m = x =~ /bla/
        return m.matches()
    }

    return "${nonCPSMatcherMethod('foo')}${cpsMatcherMethod('foo')}"
''')
        assertIntercept(''' 
Script1:___cps___0()
Script1:___cps___1()
Script1:___cps___2()
Script1.super(Script1).setBinding(Binding)
Script1.nonCPSMatcherMethod(String)
ScriptBytecodeAdapter:findRegex(String,String)
Matcher.matches()
Script1.cpsMatcherMethod(String)
ScriptBytecodeAdapter:findRegex(String,String)
Checker:checkedCast(Class,Matcher,Boolean,Boolean,Boolean)
Matcher.matches()
ScriptBytecodeAdapter:asType(ArrayList,Class)
ScriptBytecodeAdapter:asType(ArrayList,Class)
new GStringImpl(Object[],String[])
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

        String multipleArgs(String first, String second) {
            return "Hello, " + first + " " + second;
        }

        String noArg() {
            return "No argument"
        }

        String oneArg(String first) {
            return "Just one arg: " + first
        }

        public static String staticMultipleArgs(String first, String second) {
            return "Hello, " + first + " " + second;
        }

        public static String staticNoArg() {
            return "No argument"
        }

        public static String staticOneArg(String first) {
            return "Just one arg: " + first
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

        assertIntercept('''
Script1:___cps___0()
Script1:___cps___1()
Script1.super(Script1).setBinding(Binding)
new Bar()
Foo:___cps___2()
new Foo()
new SandboxInvokerTest$Base()
Bar.toString()
Bar.super(Foo).toString()
String.plus(String)
''')
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
C.p
Point.y
ScriptBytecodeAdapter:compareEqual(Double,Integer)
''')
    }

    @Issue("SECURITY-566")
    @Test public void typeCoercion() {
        ProxyGeneratorAdapter.pxyCounter.set(0); // make sure *_groovyProxy names are predictable
        evalCpsSandbox('''
            interface Static {
                Locale[] getAvailableLocales()
            }
            interface Instance {
                String getCountry()
            }
            assert (Locale as Static).getAvailableLocales() != null
            assert (Locale as Static).availableLocales != null
            assert Locale.getAvailableLocales() != null
            assert (Locale.getDefault() as Instance).getCountry() != null
            assert (Locale.getDefault() as Instance).country != null
            assert Locale.getDefault().getCountry() != null
        ''')
        // TODO recording Checker.checkedCast is undesirable, but how to avoid it?
        assertIntercept('''
Script1:___cps___0()
Script1:___cps___1()
Script1.super(Script1).setBinding(Binding)
Checker:checkedCast(Class,Class,Boolean,Boolean,Boolean)
Locale:getAvailableLocales()
Class1_groovyProxy.getAvailableLocales()
ScriptBytecodeAdapter:compareNotEqual(Locale[],null)
Checker:checkedCast(Class,Class,Boolean,Boolean,Boolean)
Locale:getAvailableLocales()
Class1_groovyProxy.availableLocales
ScriptBytecodeAdapter:compareNotEqual(Locale[],null)
Locale:getAvailableLocales()
ScriptBytecodeAdapter:compareNotEqual(Locale[],null)
Locale:getDefault()
Checker:checkedCast(Class,Locale,Boolean,Boolean,Boolean)
Locale.getCountry()
Locale2_groovyProxy.getCountry()
ScriptBytecodeAdapter:compareNotEqual(String,null)
Locale:getDefault()
Checker:checkedCast(Class,Locale,Boolean,Boolean,Boolean)
Locale.getCountry()
Locale2_groovyProxy.country
ScriptBytecodeAdapter:compareNotEqual(String,null)
Locale:getDefault()
Locale.getCountry()
ScriptBytecodeAdapter:compareNotEqual(String,null)
''')
    }

    @Issue("SECURITY-567")
    @Test
    void methodPointers() {
        evalCpsSandbox('''
import java.util.concurrent.Callable
def b = new SandboxInvokerTest.Base()
(b.&noArg)() 
(b.&multipleArgs)('Kohsuke', 'Kawaguchi') 
(b.&oneArg)('Something')
['Something'].each(b.&oneArg)
Callable c = b.&noArg
c()
def runit(Callable c) {c()}
runit({-> b.noArg()})
runit(b.&noArg)
runit({-> b.noArg()} as Callable)
runit(b.&noArg as Callable)
''')
        assertIntercept('''
Script1:___cps___0()
Script1:___cps___1()
Script1:___cps___2()
Script1.super(Script1).setBinding(Binding)
new SandboxInvokerTest$Base()
SandboxedMethodClosure.call()
SandboxInvokerTest$Base.noArg()
SandboxedMethodClosure.call(String,String)
SandboxInvokerTest$Base.multipleArgs(String,String)
SandboxedMethodClosure.call(String)
SandboxInvokerTest$Base.oneArg(String)
ArrayList.each(SandboxedMethodClosure)
SandboxInvokerTest$Base.oneArg(String)
SandboxedMethodClosure.call()
SandboxInvokerTest$Base.noArg()
Script1.runit(CpsClosure)
CpsClosure.call()
SandboxInvokerTest$Base.noArg()
Script1.runit(SandboxedMethodClosure)
SandboxedMethodClosure.call()
SandboxInvokerTest$Base.noArg()
Checker:checkedCast(Class,CpsClosure,Boolean,Boolean,Boolean)
CpsClosure.call()
Script1.runit(CpsClosure)
CpsClosure.call()
SandboxInvokerTest$Base.noArg()
Checker:checkedCast(Class,SandboxedMethodClosure,Boolean,Boolean,Boolean)
SandboxedMethodClosure.call()
Script1.runit(SandboxedMethodClosure)
SandboxedMethodClosure.call()
SandboxInvokerTest$Base.noArg()
''')
    }

    @Issue("SECURITY-567")
    @Test
    void methodPointersStatic() {
        evalCpsSandbox('''
(SandboxInvokerTest.Base.&staticMultipleArgs)('Kohsuke', 'Kawaguchi') 
(SandboxInvokerTest.Base.&staticNoArg)() 
(SandboxInvokerTest.Base.&staticOneArg)('Something') 
''')
        assertIntercept('''
Script1:___cps___0()
Script1:___cps___1()
Script1.super(Script1).setBinding(Binding)
SandboxedMethodClosure.call(String,String)
SandboxInvokerTest$Base:staticMultipleArgs(String,String)
SandboxedMethodClosure.call()
SandboxInvokerTest$Base:staticNoArg()
SandboxedMethodClosure.call(String)
SandboxInvokerTest$Base:staticOneArg(String)
''')
    }

}
