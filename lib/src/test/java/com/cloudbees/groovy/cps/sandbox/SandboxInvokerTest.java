/*
 * Copyright 2020 CloudBees, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.cloudbees.groovy.cps.sandbox;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.CpsTransformer;
import com.cloudbees.groovy.cps.Envs;
import com.cloudbees.groovy.cps.SandboxCpsTransformer;
import com.cloudbees.groovy.cps.AbstractGroovyCpsTest;
import com.cloudbees.groovy.cps.impl.FunctionCallEnv;
import java.awt.Point;
import java.io.File;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.runtime.ProxyGeneratorAdapter;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.groovy.sandbox.ClassRecorder;
import org.kohsuke.groovy.sandbox.impl.GroovyCallSiteSelector;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

public class SandboxInvokerTest extends AbstractGroovyCpsTest {
    ClassRecorder cr = new ClassRecorder();

    @Override
    protected CpsTransformer createCpsTransformer() {
        return new SandboxCpsTransformer();
    }

    @Before public void zeroIota() {
        CpsTransformer.iota.set(0);
    }

    private void evalCpsSandbox(String expression, Object expectedResult, ExceptionHandler handler) {
        FunctionCallEnv env = (FunctionCallEnv)Envs.empty();
        env.setInvoker(new SandboxInvoker());

        cr.reset();
        cr.register();
        try {
            Object actual = parseCps(expression).invoke(env, null, Continuation.HALT).run().yield.replay();
            String actualType = GroovyCallSiteSelector.getName(actual);
            String expectedType = GroovyCallSiteSelector.getName(expectedResult);
            ec.checkThat("CPS and sandbox-transformed result (" + actualType + ") does not match expected result (" + expectedType + ")", actual, equalTo(expectedResult));
        } catch (Throwable t) {
            ec.checkSucceeds(() -> {
                try {
                    handler.handleException(t);
                } catch (Throwable t2) {
                    t2.addSuppressed(t); // Keep the original error around in case an assertion fails in the handler.
                    throw t2;
                }
                return null;
            });
        } finally {
            cr.unregister();
        }
    }

    @Override
    public void assertEvaluate(Object expectedReturnValue, String script) {
        evalCpsSandbox(script, expectedReturnValue, e -> {
            throw new RuntimeException("Failed to evaluate CPS and sandboxed-transformed script: " + script, e);
        });
        // TODO: Refactor things so we can check evalCps as well.
    }

    public void assertIntercept(String script, Object expectedResult, String... expectedCalls) throws Throwable {
        assertEvaluate(expectedResult, script);
        String[] updatedExpectedCalls = expectedCalls;
        // Insert SerializableScript constructor call automatically to avoid having to update all tests.
        if (expectedCalls.length == 0 || (expectedCalls.length > 0 && !expectedCalls[0].equals("new SerializableScript()"))) {
            updatedExpectedCalls = new String[expectedCalls.length + 1];
            updatedExpectedCalls[0] = "new SerializableScript()";
            System.arraycopy(expectedCalls, 0, updatedExpectedCalls, 1, expectedCalls.length);
        }
        ec.checkThat(cr.toString().split("\n"), equalTo(updatedExpectedCalls));
    }

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
            List.of(new Point(1, 4), new File("foo")),
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
        evalCpsSandbox("class Test { @Override public void finalize() { } }; null", ShouldFail.class, t -> {
            assertThat(t, instanceOf(MultipleCompilationErrorsException.class));
            MultipleCompilationErrorsException e = (MultipleCompilationErrorsException) t;
            assertThat(e.getErrorCollector().getErrorCount(), equalTo(1));
            Exception innerE = e.getErrorCollector().getException(0);
            assertThat(innerE, instanceOf(SecurityException.class));
            assertThat(innerE.getMessage(), containsString("Object.finalize()"));
        });
    }

    @Issue("SECURITY-1186")
    @Test
    public void nonCpsfinalizerForbidden() throws Throwable {
        evalCpsSandbox("class Test { @Override @NonCPS public void finalize() { } }; null", ShouldFail.class, t -> {
            assertThat(t, instanceOf(MultipleCompilationErrorsException.class));
            MultipleCompilationErrorsException e = (MultipleCompilationErrorsException) t;
            assertThat(e.getErrorCollector().getErrorCount(), equalTo(1));
            Exception innerE = e.getErrorCollector().getException(0);
            assertThat(innerE, instanceOf(SecurityException.class));
            assertThat(innerE.getMessage(), containsString("Object.finalize()"));
        });
    }

    @Issue("SECURITY-1710")
    @Test public void methodParametersWithInitialExpressions() throws Throwable {
        assertIntercept(
                "def m(p = System.getProperties()){ true }; m()",
                true,
                "Script1.super(Script1).setBinding(Binding)",
                "Script1.m()",
                "System:getProperties()",
                "Script1.m(Properties)");
    }

    @Test public void constructorParametersWithInitialExpressions() throws Throwable {
        assertIntercept(
                "class Test {\n" +
                "  Test(p = System.getProperties()) { }" +
                "}\n" +
                "new Test()\n" +
                "null",
                null,
                "Script1.super(Script1).setBinding(Binding)",
                "new Test()",
                "System:getProperties()",
                "new Test(Properties)");
    }

    @Ignore("Initial expressions for parameters in CPS-transformed closures are currently ignored")
    @Test public void closureParametersWithInitialExpressions() throws Throwable {
        // Fails because p is null in the body of the closure.
        assertIntercept(
                "{ p = System.getProperties() -> p != null }()",
                true,
                "Script1.super(Script1).setBinding(Binding)",
                "CpsClosure.call()",
                "System:getProperties()", // Not currently intercepted because it is dropped by the transformer.
                "ScriptBytecodeAdapter:compareNotEqual(null,null)");
    }

    @Issue("SECURITY-2824")
    @Test public void sandboxInterceptsImplicitCastsVariableAssignment() throws Throwable {
        assertIntercept(
                "File file\n" + // DeclarationExpression
                "file = ['secret.key']\n " + // BinaryExpression
                "file",
                new File("secret.key"),
                "Script1.super(Script1).setBinding(Binding)",
                "new File(String)");
    }

    @Issue("SECURITY-2824")
    @Test
    public void sandboxInterceptsImplicitCastsArrayAssignment() throws Throwable {
        // Regular Groovy casts the rhs of array assignments to match the component type of the array, but the
        // sandbox does not do this (with or without the CPS transformation). Ideally the sandbox would have the same
        // behavior as regular Groovy, but the current behavior is safe, which is good enough.
        evalCpsSandbox(
            "File[] files = [null]\n" +
            "files[0] = ['secret.key']\n " +
            "files[0]",
            ShouldFail.class,
            t -> {
                assertEquals("java.lang.ArrayStoreException: java.util.ArrayList", t.toString());
            });
    }

    @Issue("SECURITY-2824")
    @Test public void sandboxInterceptsImplicitCastsInitialParameterExpressions() throws Throwable {
        assertIntercept(
                "def method(File file = ['secret.key']) { file }; method()",
                new File("secret.key"),
                "Script1.super(Script1).setBinding(Binding)",
                "Script1.method()",
                "new File(String)",
                "Script1.method(File)");
        // The CPS transformation currently ignores Closure parameter initial expressions.
        assertIntercept(
                "({ File file = ['secret.key'] -> file })()",
                (Object)null,
                "Script2.super(Script2).setBinding(Binding)",
                "CpsClosure.call()");
                // "new File(String)" This should also be intercepted if initial expressions are supported
        assertIntercept(
                "class Test {\n" +
                "  def x\n" +
                "  Test(File file = ['secret.key']) {\n" +
                "   x = file\n" +
                "  }\n" +
                "}\n" +
                "new Test().x",
                new File("secret.key"),
                "Script3.super(Script3).setBinding(Binding)",
                "new Test()",
                "new File(String)",
                "new Test(File)",
                "Test.x");
    }

    @Issue("SECURITY-2824")
    @Test public void sandboxInterceptsImplicitCastsFields() throws Throwable {
        assertIntercept(
                "class Test {\n" +
                "  File file = ['secret.key']\n" +
                "}\n" +
                "new Test().file",
                new File("secret.key"),
                "Script1.super(Script1).setBinding(Binding)",
                "new Test()",
                "new File(String)",
                "Test.file");
        assertIntercept(
                "@groovy.transform.Field File file = ['secret.key']\n" +
                "file",
                new File("secret.key"),
                "new File(String)",
                "Script2.super(Script2).setBinding(Binding)",
                "Script2.file");
    }

    @Issue("SECURITY-2824")
    @Test public void sandboxInterceptsArrayCastsRecursively() throws Throwable {
        assertIntercept(
                "([['secret.key']] as File[])[0]",
                new File("secret.key"),
                "Script1.super(Script1).setBinding(Binding)",
                "new File(String)",
                "File[][Integer]");
    }

    @Test public void sandboxInterceptsBooleanCasts() throws Throwable {
        assertIntercept("if ([:]) { true } else { false }",
                false,
                "Script1.super(Script1).setBinding(Binding)",
                "LinkedHashMap.asBoolean()");
        assertIntercept("if (['a' : 1]) { true } else { false }",
                true,
                "Script2.super(Script2).setBinding(Binding)",
                "LinkedHashMap.asBoolean()");
    }

    @Test public void dynamicMethodPointer() throws Throwable {
        assertIntercept(
                "def method3() {\n" +
                "    true\n" +
                "}\n" +
                "def mp = this.&/method${1 + 2}/\n" +
                "mp()",
                true,
                "Script1.super(Script1).setBinding(Binding)",
                "Integer.plus(Integer)",
                "new GStringImpl(Object[],String[])",
                "SandboxedMethodClosure.call()",
                "Script1.method3()");
    }

    @Issue("JENKINS-70108")
    @Test public void castsInTrustedCodeCalledByUntrustedCodeShouldNotBeIntercepted() throws Throwable {
        TrustedCpsCompiler trusted = new TrustedCpsCompiler();
        trusted.setUp();
        getBinding().setVariable("trusted", trusted.getCsh().parse("def foo() { File f = ['secret.key'] }"));
        assertIntercept(
            "trusted.foo()", // Untrusted script
            new File("secret.key"),
            "Script1.super(Script1).setBinding(Binding)",
            "Script1.trusted",
            "Script1.foo()");
    }

    /*
     * All blocks that perform boolean casts internally using ContinuationGroup.castToBoolean incorrectly intercept
     * calls performed by the cast when the cast is in trusted code that is called by untrusted code. For boolean
     * casts, this is not that interesting, since it only causes problems in practice if you cast a type that
     * implements an asBoolean method that would not be allowed by the sandbox.
     * I see two obvious ways to fix this:
     * 1. Add a CallSiteBlock parameter to ContinuationGroup.castToBoolean, and use it to contextualize the invoker
     *    used in that method. All Blocks that use the method would need to be updated to implement CallSiteBlock.
     * 2. Delete ContinuationGroup.castToBoolean and replace all uses with basic Java casts to boolean. Modify
     *    CpsTransformer to insert explicit boolean casts into the CPS-transformed program for all AST nodes whose
     *    Blocks previously used castToBoolean.
     */
    @Ignore("This variant of JENKINS-70108 seems less likely to cause problems in practice and is more complex to fix")
    @Test public void booleanCastsInTrustedCodeCalledByUntrustedCodeShouldNotBeIntercepted() throws Throwable {
        TrustedCpsCompiler trusted = new TrustedCpsCompiler();
        trusted.setUp();
        getBinding().setVariable("trusted", trusted.getCsh().parse(
                "class Test { @NonCPS def asBoolean() { false } }\n" +
                "def foo() { if (new Test()) { 123 } else { 456 } }"));
        assertIntercept(
            "trusted.foo()", // Untrusted script
            456,
            "Script1.super(Script1).setBinding(Binding)",
            "Script1.trusted",
            "Script1.foo()");
            // Currently the call to Test.asBoolean() is also intercepted, which is incorrect.
    }
}
