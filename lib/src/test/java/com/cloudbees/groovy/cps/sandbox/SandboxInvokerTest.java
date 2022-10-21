package com.cloudbees.groovy.cps.sandbox;

import com.cloudbees.groovy.cps.AbstractGroovyCpsTest;
import java.awt.Point;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.runtime.ProxyGeneratorAdapter;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.groovy.sandbox.ClassRecorder;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;

/**
 * @author Kohsuke Kawaguchi
 */
public class SandboxInvokerTest extends SandboxInvoker2Test {
    ClassRecorder cr = new ClassRecorder();

    /**
     * Covers all the intercepted operations.
     */
    @Test
    public void basic() throws Throwable {
        assertIntercept(
            "import java.awt.Point;\n" +
            "\n" +
            "def p = new Point(1,3);\n" +     // constructor
            "assert p.equals(p)\n" +          // method call
            "assert 4 == p.x+p.y;\n" +        // property get
            "p.x = 5;\n" +                    // property set
            "assert 5 == p.@x;\n" +           // attribute get
            "p.@x = 6;\n" +                   // attribute set
            "\n" +
            "def a = new int[3];\n" +
            "a[1] = a[0]+7;\n" +              // array get & set
            "assert a[1]==7;\n",
            null,
            "Script1.super(Script1).setBinding(Binding)",
            "new Point(Integer,Integer)",
            "Point.equals(Point)",
            "Point.x",
            "Point.y",
            "Double.plus(Double)",
            "ScriptBytecodeAdapter:compareEqual(Integer,Double)",
            "Point.x=Integer",
            "Point.@x",
            "ScriptBytecodeAdapter:compareEqual(Integer,Integer)",
            "Point.@x=Integer",
            "int[][Integer]",
            "Integer.plus(Integer)",
            "int[][Integer]=Integer",
            "int[][Integer]",
            "ScriptBytecodeAdapter:compareEqual(Integer,Integer)");
    }

    @Test
    public void mixtureOfNonTransformation() throws Throwable {
        assertIntercept(
            "@NonCPS\n" +
            "def length(x) {\n" +
            "    return x.length();\n" +
            "}\n" +
            "return length('foo')\n",
            3,
            "Script1.super(Script1).setBinding(Binding)",
            "Script1.length(String)",
            "String.length()");
    }

    @Issue("JENKINS-46088")
    @Test
    public void matcherTypeAssignment() throws Throwable {
        assertIntercept(
            "@NonCPS\n" +
            "def nonCPSMatcherMethod(String x) {\n" +
            "   java.util.regex.Matcher m = x =~ /bla/\n" +
            "   return m.matches()\n" +
            "}\n" +
            "def cpsMatcherMethod(String x) {\n" +
            "    java.util.regex.Matcher m = x =~ /bla/\n" +
            "    return m.matches()\n" +
            "}\n" +
            "return \"${nonCPSMatcherMethod('foo')}${cpsMatcherMethod('foo')}\".toString()\n",
            "falsefalse",
            "Script1.super(Script1).setBinding(Binding)",
            "Script1.nonCPSMatcherMethod(String)",
            "ScriptBytecodeAdapter:findRegex(String,String)",
            "Matcher.matches()",
            "Script1.cpsMatcherMethod(String)",
            "ScriptBytecodeAdapter:findRegex(String,String)",
            "Matcher.matches()",
            "new GStringImpl(Object[],String[])",
            "GStringImpl.toString()");
    }

    static class TrustedCpsCompiler extends AbstractGroovyCpsTest {
    }

    /**
     * Untrusted code -> trusted code -> untrusted code.
     */
    @Test
    public void mixingTrustedAndUntrusted() throws Throwable {
        TrustedCpsCompiler trusted = new TrustedCpsCompiler();
        trusted.setUp();

        SandboxInvokerTest untrusted = this;

        untrusted.getBinding().setVariable("trusted",  trusted.getCsh().parse("def foo(x) { return [new java.awt.Point(1,x),untrusted.bar()] }"));
        cr.register(); // untrusted.csh.parse instantiates the sandbox-transformed script, so a GroovyInterceptor must be registered when it runs.
        try {
          trusted.getBinding().setVariable("untrusted",untrusted.getCsh().parse("def bar() { return new File('foo') }"));
        } finally {
           cr.unregister();
        }

        assertIntercept(
            "trusted.foo(4)",
            Arrays.asList(new Point(1, 4), new File("foo")),
            //"Script1.super(Script1).setBinding(Binding)",
            "Script2.super(Script2).setBinding(Binding)",
            "Script2.trusted",
            "Script1.foo(Integer)",
            "new File(String)");
    }


    public static class Base {
        @Override
        public String toString() {
            return "base";
        }

        String multipleArgs(String first, String second) {
            return "Hello, " + first + " " + second;
        }

        String noArg() {
            return "No argument";
        }

        String oneArg(String first) {
            return "Just one arg: " + first;
        }

        public static String staticMultipleArgs(String first, String second) {
            return "Hello, " + first + " " + second;
        }

        public static String staticNoArg() {
            return "No argument";
        }

        public static String staticOneArg(String first) {
            return "Just one arg: " + first;
        }
    }
    @Test
    public void superClass() throws Throwable {
        assertIntercept(
            "class Foo extends SandboxInvokerTest.Base {\n" +
            "    public String toString() {\n" +
            "        return 'x'+super.toString();\n" +
            "    }\n" +
            "}\n" +
            "class Bar extends Foo {}\n" +
            "new Bar().toString();\n",
            (Object) "xbase",
            "Script1.super(Script1).setBinding(Binding)",
            "new Bar()",
            "new Foo()",
            "new SandboxInvokerTest$Base()",
            "Bar.toString()",
            "Bar.super(Foo).toString()",
            "String.plus(String)");
    }

    @Issue("JENKINS-45982")
    @Test
    public void transformedSuperClass() throws Throwable {
        assertIntercept(
            "class Foo extends SandboxInvokerTest.Base {\n" +
            "    public String other() {\n" +
            "        return 'base'\n" +
            "    }\n" +
            "}\n" +
            "class Bar extends Foo {\n" +
            "    public String other() {\n" +
            "        return 'y'+super.other()\n" +
            "    }\n" +
            "}\n" +
            "new Bar().other();\n",
            (Object) "ybase",
            "Script1.super(Script1).setBinding(Binding)",
            "new Bar()",
            "new Foo()",
            "new SandboxInvokerTest$Base()",
            "Bar.other()",
            "Bar.super(Bar).other()",
            "String.plus(String)");
    }

    @Issue("SECURITY-551")
    @Test public void constructors() throws Throwable {
        assertIntercept(
            "import java.awt.Point;\n" +
            "class C {\n" +
            "    Point p\n" +
            "    C() {\n" +
            "        p = new Point(1, 3)\n" +
            "    }\n" +
            "}\n" +
            "assert new C().p.y == 3\n",
            (Object) null,
            "Script1.super(Script1).setBinding(Binding)",
            "new C()",
            "new Point(Integer,Integer)",
            "C.p",
            "Point.y",
            "ScriptBytecodeAdapter:compareEqual(Double,Integer)");
    }

    @Issue("SECURITY-551")
    @Test public void fields() throws Throwable {
        assertIntercept(
            "import java.awt.Point;\n" +
            "class C {\n" +
            "    Point p = new Point(1, 3)\n" +
            "}\n" +
            "assert new C().p.y == 3\n",
            (Object) null,
            "Script1.super(Script1).setBinding(Binding)",
            "new C()",
            "new Point(Integer,Integer)",
            "C.p",
            "Point.y",
            "ScriptBytecodeAdapter:compareEqual(Double,Integer)");
    }

    @Issue("SECURITY-551")
    @Test public void initializers() throws Throwable {
        assertIntercept(
            "import java.awt.Point;\n" +
            "class C {\n" +
            "    Point p\n" +
            "    {\n" +
            "        p = new Point(1, 3)\n" +
            "    }\n" +
            "}\n" +
            "assert new C().p.y == 3\n",
            (Object) null,
            "Script1.super(Script1).setBinding(Binding)",
            "new C()",
            "new Point(Integer,Integer)",
            "C.p",
            "Point.y",
            "ScriptBytecodeAdapter:compareEqual(Double,Integer)");
    }

    @Issue("SECURITY-566")
    @Test public void typeCoercion() throws Throwable {
        Field pxyCounterField = ProxyGeneratorAdapter.class.getDeclaredField("pxyCounter");
        pxyCounterField.setAccessible(true);
        AtomicLong pxyCounterValue = (AtomicLong) pxyCounterField.get(null);
        pxyCounterValue.set(0); // make sure *_groovyProxy names are predictable
        assertIntercept(
            "interface Static {\n" +
            "    Locale[] getAvailableLocales()\n" +
            "}\n" +
            "interface Instance {\n" +
            "    String getCountry()\n" +
            "}\n" +
            "assert (Locale as Static).getAvailableLocales() != null\n" +
            "assert (Locale as Static).availableLocales != null\n" +
            "assert Locale.getAvailableLocales() != null\n" +
            "assert (Locale.getDefault() as Instance).getCountry() != null\n" +
            "assert (Locale.getDefault() as Instance).country != null\n" +
            "assert Locale.getDefault().getCountry() != null\n",
            (Object) null,
            "Script1.super(Script1).setBinding(Binding)",
            "Locale:getAvailableLocales()",
            "Class1_groovyProxy.getAvailableLocales()",
            "ScriptBytecodeAdapter:compareNotEqual(Locale[],null)",
            "Locale:getAvailableLocales()",
            "Class1_groovyProxy.availableLocales",
            "ScriptBytecodeAdapter:compareNotEqual(Locale[],null)",
            "Locale:getAvailableLocales()",
            "ScriptBytecodeAdapter:compareNotEqual(Locale[],null)",
            "Locale:getDefault()",
            "Locale.getCountry()",
            "Locale2_groovyProxy.getCountry()",
            "ScriptBytecodeAdapter:compareNotEqual(String,null)",
            "Locale:getDefault()",
            "Locale.getCountry()",
            "Locale2_groovyProxy.country",
            "ScriptBytecodeAdapter:compareNotEqual(String,null)",
            "Locale:getDefault()",
            "Locale.getCountry()",
            "ScriptBytecodeAdapter:compareNotEqual(String,null)");
    }

    @Issue("SECURITY-567")
    @Test
    public void methodPointers() throws Throwable {
        assertIntercept(
            "import java.util.concurrent.Callable\n" +
            "def b = new SandboxInvokerTest.Base()\n" +
            "(b.&noArg)() \n" +
            "(b.&multipleArgs)('Kohsuke', 'Kawaguchi') \n" +
            "(b.&oneArg)('Something')\n" +
            "['Something'].each(b.&oneArg)\n" +
            "Callable c = b.&noArg\n" +
            "c()\n" +
            "def runit(Callable c) {c()}\n" +
            "runit({-> b.noArg()})\n" +
            "runit(b.&noArg)\n" +
            "runit({-> b.noArg()} as Callable)\n" +
            "runit(b.&noArg as Callable)\n",
            (Object) "No argument",
            "Script1.super(Script1).setBinding(Binding)",
            "new SandboxInvokerTest$Base()",
            "SandboxedMethodClosure.call()",
            "SandboxInvokerTest$Base.noArg()",
            "SandboxedMethodClosure.call(String,String)",
            "SandboxInvokerTest$Base.multipleArgs(String,String)",
            "SandboxedMethodClosure.call(String)",
            "SandboxInvokerTest$Base.oneArg(String)",
            "ArrayList.each(SandboxedMethodClosure)",
            "SandboxInvokerTest$Base.oneArg(String)",
            "SandboxedMethodClosure.call()",
            "SandboxInvokerTest$Base.noArg()",
            "Script1.runit(CpsClosure)",
            "CpsClosure.call()",
            "SandboxInvokerTest$Base.noArg()",
            "Script1.runit(SandboxedMethodClosure)",
            "SandboxedMethodClosure.call()",
            "SandboxInvokerTest$Base.noArg()",
            "Script1.runit(CpsClosure)",
            "CpsClosure.call()",
            "SandboxInvokerTest$Base.noArg()",
            "Script1.runit(SandboxedMethodClosure)",
            "SandboxedMethodClosure.call()",
            "SandboxInvokerTest$Base.noArg()");
    }

    @Issue("SECURITY-567")
    @Test
    public void methodPointersStatic() throws Throwable {
        assertIntercept(
            "(SandboxInvokerTest.Base.&staticMultipleArgs)('Kohsuke', 'Kawaguchi')\n" +
            "(SandboxInvokerTest.Base.&staticNoArg)()\n" +
            "(SandboxInvokerTest.Base.&staticOneArg)('Something')\n",
            (Object) "Just one arg: Something",
            "Script1.super(Script1).setBinding(Binding)",
            "SandboxedMethodClosure.call(String,String)",
            "SandboxInvokerTest$Base:staticMultipleArgs(String,String)",
            "SandboxedMethodClosure.call()",
            "SandboxInvokerTest$Base:staticNoArg()",
            "SandboxedMethodClosure.call(String)",
            "SandboxInvokerTest$Base:staticOneArg(String)");
    }

    @Issue("JENKINS-45575")
    @Test
    public void sandboxedMultipleAssignment() throws Throwable {
        assertIntercept(
            "def (a, b) = ['first', 'second']\n" +
            "def c, d\n" +
            "(c, d) = ['third', 'fourth']\n" +
            "return a + b + c + d\n",
            (Object) "firstsecondthirdfourth",
            "Script1.super(Script1).setBinding(Binding)",
            "ArrayList[Integer]",
            "ArrayList[Integer]",
            "ArrayList[Integer]",
            "ArrayList[Integer]",
            "String.plus(String)",
            "String.plus(String)", 
            "String.plus(String)");
    }

    @Issue("JENKINS-45575")
    @Test
    public void typeCoercionMultipleAssignment() throws Throwable {
        Field pxyCounterField = ProxyGeneratorAdapter.class.getDeclaredField("pxyCounter");
        pxyCounterField.setAccessible(true);
        AtomicLong pxyCounterValue = (AtomicLong) pxyCounterField.get(null);
        pxyCounterValue.set(0); // make sure *_groovyProxy names are predictable
        assertIntercept(
            "interface Static {\n" +
            "    Locale[] getAvailableLocales()\n" +
            "}\n" +
            "interface Instance {\n" +
            "    String getCountry()\n" +
            "}\n" +
            "def (a, b) = [Locale as Static, Locale.getDefault() as Instance]\n" +
            "assert a.getAvailableLocales() != null\n" +
            "assert b.country != null\n",
            (Object) null,
            "Script1.super(Script1).setBinding(Binding)",
            "Locale:getAvailableLocales()",
            "Locale:getDefault()",
            "Locale.getCountry()",
            "ArrayList[Integer]",
            "ArrayList[Integer]",
            "Class1_groovyProxy.getAvailableLocales()",
            "ScriptBytecodeAdapter:compareNotEqual(Locale[],null)",
            "Locale2_groovyProxy.country",
            "ScriptBytecodeAdapter:compareNotEqual(String,null)");
    }

    @Issue("JENKINS-49679")
    @Test
    public void sandboxedMultipleAssignmentRunsMethodOnce() throws Throwable {
        assertIntercept(
            "alreadyRun = false\n" +
            "def getAandB() {\n" +
            "  if (!alreadyRun) {\n" +
            "    alreadyRun = true\n" +
            "    return ['first', 'second']\n" +
            "  } else {\n" +
            "    return ['bad', 'worse']\n" +
            "  }\n" +
            "}\n" +
            "def (a, b) = getAandB()\n" +
            "def c, d\n" +
            "(c, d) = ['third', 'fourth']\n" +
            "return a + b + c + d\n",
            (Object) "firstsecondthirdfourth",
            "Script1.super(Script1).setBinding(Binding)",
            "Script1.alreadyRun=Boolean",
            "Script1.getAandB()",
            "Script1.alreadyRun",
            "Script1.alreadyRun=Boolean",
            "ArrayList[Integer]",
            "ArrayList[Integer]",
            "ArrayList[Integer]",
            "ArrayList[Integer]",
            "String.plus(String)",
            "String.plus(String)",
            "String.plus(String)");
    }

    @Issue("SECURITY-1186")
    @Test
    public void finalizerForbidden() throws Throwable {
        try {
            evalCpsSandbox("class Test { @Override public void finalize() { } }; null");
            fail("Finalizers should be rejected");
        } catch (MultipleCompilationErrorsException e) {
            assertThat(e.getErrorCollector().getErrorCount(), equalTo(1));
            Exception innerE = e.getErrorCollector().getException(0);
            assertThat(innerE, instanceOf(SecurityException.class));
            assertThat(innerE.getMessage(), containsString("Object.finalize()"));
        }
    }

    @Issue("SECURITY-1186")
    @Test
    public void nonCpsfinalizerForbidden() throws Throwable {
        try {
            evalCpsSandbox("class Test { @Override @NonCPS public void finalize() { } }; null");
            fail("Finalizers should be rejected");
        } catch (MultipleCompilationErrorsException e) {
            assertThat(e.getErrorCollector().getErrorCount(), equalTo(1));
            Exception innerE = e.getErrorCollector().getException(0);
            assertThat(innerE, instanceOf(SecurityException.class));
            assertThat(innerE.getMessage(), containsString("Object.finalize()"));
        }
    }

}
