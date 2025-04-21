package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import groovy.lang.IntRange;
import java.io.File;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public class CpsTransformerTest extends AbstractGroovyCpsTest {

    @Test
    public void helloWorld() throws Throwable {
        assertEvaluate(11, "'hello world'.length()");
    }

    @Test
    public void comparison() throws Throwable {
        for(int i : new int[] { 1, 2, 3 }) {
            for (int j : new int[] { 1, 2, 3 }) {
                assertEvaluate(i < j, i + " < " + j);
                assertEvaluate(i <= j, i + " <= " + j);
                assertEvaluate(i > j, i + " > " + j);
                assertEvaluate(i >= j, i + " >=" + j);
            }
        }
    }

    @Test
    public void forInLoop() throws Throwable {
        assertEvaluate(15, "x=0; for (i in [1,2,3,4,5]) x+=i; return x;");
    }

    @Test
    public void variableAssignment() throws Throwable {
        assertEvaluate(5, "x=3; x+=2; return x;");
    }

    @Test
    public void localVariable() throws Throwable {
        assertEvaluate(5, "int x=3; x+=2; return x;");
    }

    @Test
    public void increment() throws Throwable {
        assertEvaluate("2.0.2",
            "x=0;\n" +
            "y = x++;\n" +
            "z = ++x;\n" +
            "return x+'.'+y+'.'+z;\n");
    }

    @Test
    public void decrement() throws Throwable {
        assertEvaluate("3.5.3",
            "x=5;\n" +
            "y = x--;\n" +
            "z = --x;\n" +
            "return x+'.'+y+'.'+z;\n");
    }

    @Test
    public void break_() throws Throwable {
        assertEvaluate(0,
            "x=0;\n" +
            "int i=0;\n" +
            "for (i=0; i<5; i+=1) {\n" +
            "    break;\n" +
            "    x+=1;\n" +
            "}\n" +
            "return i+x;\n");
    }

    @Test
    public void globalBreak_() throws Throwable {
        assertEvaluate("0.0.0",
            "x=0;\n" +
            "int i=0;\n" +
            "int j=0;\n" +
            "I:\n" +
            "for (i=0; i<5; i+=1) {\n" +
            "    J:\n" +
            "    for (j=0; j<5; j+=1) {\n" +
            "      break I;\n" +
            "      x+=1;\n" +
            "    }\n" +
            "    x+=1;\n" +
            "}\n" +
            "return i+'.'+j+'.'+x;\n");
    }

    @Test
    public void functionCall() throws Throwable {
        assertEvaluate(3,
            "int i=1;\n" +
            "i.plus(2)");
    }

    @Test
    public void functionCall0arg() throws Throwable {
        assertEvaluate("123", "123.toString()");
    }

    @Test
    public void constructorCall() throws Throwable {
        assertEvaluate("abcdef",  "new String('abc'+'def')");
    }

    @Test
    public void constructorCall0arg() throws Throwable {
        assertEvaluate("", "new String()");
    }

    @Issue("https://github.com/cloudbees/groovy-cps/issues/31")
    @Test
    public void constructorList() throws Throwable {
        assertEvaluate(new File("/parent", "name"),
            "File f = ['/parent', 'name']\n" +
            "return f");

        // Test the closure env
        assertEvaluate(new File("/parent", "name"),
            "def close = {String parent, String name -> [parent, name] as File}\n" +
            "return close('/parent', 'name')\n");
    }

    @Test
    public void workflowCallingWorkflow() throws Throwable {
        assertEvaluate(55,
            "def fib(int x) {\n" +
            "  if (x==0)     return 0;\n" +
            "  if (x==1)     return 1;\n" +
            "  x = fib(x-1)+fib(x-2);\n" +    // assignment to make sure x is treated as local variable
            "  return x;\n" +
            "}\n" +
            "fib(10);\n");
    }

    @Test public void typeCoercion() throws Throwable {
        assertEvaluate(Locale.getAvailableLocales(),
            "interface I {\n" +
            "    Locale[] getAvailableLocales()\n" +
            "}\n" +
            "try {\n" +
            "    (Locale as I).getAvailableLocales()\n" +
            "} catch (e) {\n" +
            "    e.toString()\n" +
            "}\n");
    }

    /**
     *
     */
    @Test
    public void exceptionFromNonCpsCodeShouldBeCaughtByCatchBlockInCpsCode() throws Throwable {
        String message = (String) evalCPSonly(
            "def foo() {\n" +
            "  'abc'.substring(5);\n" + // will caught exception
            "  return 'fail';\n" +
            "}\n" +
            "try {\n" +
            "  return foo();\n" +
            "} catch(StringIndexOutOfBoundsException e) {\n" +
            "  return e.message;\n" +
            "}\n");
        assertThat(message, anyOf(
                equalTo("String index out of range: -2"), // Before Java 14
                equalTo("begin 5, end 3, length 3"), // Before Java 18
                equalTo("Range [5, 3) out of bounds for length 3"))); // Later versions
    }

    /**
     * while loop that evaluates to false and doesn't go through the body
     */
    @Test
    public void whileLoop() throws Throwable {
        assertEvaluate(1,
            "int x=1;\n" +
            "while (false) {\n" +
            "    x++;\n" +
            "}\n" +
            "return x;\n");
    }

    /**
     * while loop that goes through several iterations.
     */
    @Test
    public void whileLoop5() throws Throwable {
        assertEvaluate(5,
            "int x=1;\n" +
            "while (x<5) {\n" +
            "    x++;\n" +
            "}\n" +
            "return x;\n");
    }

    /**
     * do-while loop that evaluates to false immediately
     */
    @Test
    @Ignore("Groovy 2.x does not support do-while loops")
    public void doWhileLoop() throws Throwable {
        assertEvaluate(2,
            "int x=1;\n" +
            "do {\n" +
            "    x++;\n" +
            "} while (false);\n" +
            "return x;\n");
    }

    /**
     * do/while loop that goes through several iterations.
     */
    @Test
    @Ignore("Groovy 2.x does not support do-while loops")
    public void dowhileLoop5() throws Throwable {
        assertEvaluate(5,
            "int x=1;\n" +
            "do {\n" +
            "    x++;\n" +
            "} while (x<5);\n" +
            "return x;\n");
    }

    @Test
    public void helloClosure() throws Throwable {
        assertEvaluate(5,
            "x = { -> 5 }\n" +
            "return x();\n");
    }

    @Test
    public void closureShouldCaptureLiveVariables() throws Throwable {
        assertEvaluate("0.3.5",
            "def c1,c2;\n" +
            "{ ->\n" +
            "    def x = 0;\n" +
            "    c1 = { return x; }\n" +
            "    c2 = { v -> x=v; }\n" +
            "}();\n" +
            "r = ''+c1();\n" +
            "c2(3);\n" +
            "r += '.'+c1();\n" +
            "c2(5);\n" +
            "r += '.'+c1();\n" +
            "return r;\n");
    }

    @Test
    public void closureHasImplicitItVariable() throws Throwable {
        assertEvaluate(4,
            "c = { it+1 }\n" +
            "c(3);\n");
    }

    /**
     * A common pattern of using closure as a configuration block requires
     * a library to set a delegate.
     */
    @Test
    public void closureDelegateProperty() throws Throwable {
        assertEvaluate("3-xyz-xyz-true",
            "def config(c) {\n" +
            "    def map = [:];\n" +
            "    c.resolveStrategy = Closure.DELEGATE_FIRST;\n" +
            "    c.delegate = map;\n" +
            "    c();\n" +
            "    return map;\n" +
            "}\n" +
            "def x = config {\n" +
            "    foo = 3;\n" +
            "    bar = 'xyz';\n" +
            "    zot = bar;\n" +
            "    fog = containsKey('foo');\n" +
            "}\n" +
            "return [x.foo, x.bar, x.zot, x.fog].join('-');\n");
    }

    @Test
    public void serialization() throws Throwable {
        CpsCallableInvocation s = parseCps(
            "def plus3(int x) {\n" +
            "    return x+3;\n" +
            "}\n" +
            "for (int x=0; x<10; x++) {\n" + // meaningless code to cram as much coding construct as possible
            "    try {\n" +
            "        while (false)\n" +
            "            ;\n" +
            "    } catch(Exception e) {\n" +
            "        ;\n" +
            "    }\n" +
            "}\n" +
            "1+plus3(3*2)\n");
        Continuable cx = new Continuable(s.invoke(null, null, Continuation.HALT));
        cx = roundtripSerialization(cx);
        assertEquals(10, cx.run(null));
    }

    @Test
    public void assertion() throws Throwable {
        // when assertion passes
        assertEvaluate(3,
            "assert true\n" +
            "assert true : 'message'\n" +
            "return 3;\n");

        evalCps("assert 1+2 == ((4));", ShouldFail.class, t -> {
            ec.checkThat(t, instanceOf(AssertionError.class));
            ec.checkThat(t.getMessage(), containsString("1+2 == ((4))"));
        });

        evalCps("assert (1+2) == 4 : 'with message';", ShouldFail.class, t -> {
            ec.checkThat(t, instanceOf(AssertionError.class));
            ec.checkThat(t.getMessage(), containsString("with message. Expression: assert (1+2) == 4 : 'with message'"));
        });
    }

    @Test
    public void unaryOps() throws Throwable {
        assertEvaluate(0,
            "def x = 5;\n" +
            "def y = -x;\n" +
            "def z = +x;\n" +
            "return y+z;\n");
    }

    @Test
    public void not() throws Throwable {
        assertEvaluate("y=false,z=true",
            "def x = true;\n" +
            "def y = !x;\n" +
            "def z = !y;\n" +
            "return 'y='+y+',z='+z;\n");
    }

    @Test
    public void bitwiseNegative() throws Throwable {
        assertEvaluate(-33,
            "int x = 32;\n" +
            "return ~x;\n");
    }

    @Test
    public void gstring() throws Throwable {
        assertEvaluate("hello 4=foo",
            "def x = 'foo';\n" +
            "return \"hello ${1+3}=${x}\".toString()\n");
    }

    @Issue("https://github.com/cloudbees/groovy-cps/issues/15")
    @Test
    public void gstringWithStringWriterClosure() throws Throwable {
        String script =
            "String text = 'Foobar';\n" +
            "String result = /${ w -> w << text}/.toString();\n" +
            "return result;\n";
        assertEvaluate("Foobar", script);
    }

    @Test
    public void ternaryOp() throws Throwable {
        assertEvaluate(5, "return true ? 5 : null.makeCall();");
    }

    @Test
    public void ternaryOp2() throws Throwable {
        assertEvaluate("zot", "false ? bogus.noSuchCall() : 'zot'");
    }

    @Test
    public void ternaryOp3() throws Throwable {
        assertEvaluate(List.of("ok", 2), "def x = 'ok'; def y = null; [x ?: 1, y ?: 2]");
    }

    @Test
    public void elvisOp() throws Throwable {
        assertEvaluate(1, "def x=0; return ++x ?: -1");
        assertEvaluate(-1, "def x=0; return x++ ?: -1");
    }

    @Test public void logicalOp() throws Throwable {
        assertEvaluate(false, "true && (false || false)");
        assertEvaluate(true, "true && (true || false)");
        assertEvaluate(false, "false && (true || false)");
        assertEvaluate(true, "false || 'rhs of || must be cast to boolean'");
        assertEvaluate(true, "true && 'rhs of && must be cast to boolean'");
        assertEvaluate("[true, false, true, true] [1, 0, 0, 4]",
            "x = [0, 0, 0, 0]\n" +
            "def set(index) {\n" +
            "    x[index - 1] = index\n" +
            "    true\n" +
            "}\n" +
            "def r = [\n" +
            "    true  && set(1),\n" +
            "    false && set(2),\n" +
            "    true  || set(3),\n" +
            "    false || set(4)\n" +
            "]\n" +
            "\"${r} ${x}\".toString()\n");
    }

    @Test
    public void range() throws Throwable {
        assertEvaluate(new IntRange(true, 0, 5), "def x=5; return (0..x)");
        assertEvaluate(new IntRange(false, 0, 5), "def x=5; return (0..<x)");
    }

    @Test
    public void minusEqual() throws Throwable {
        assertEvaluate(2, "def x=5; x-=3; return x;");
    }

    @Test
    public void multiplyEqual() throws Throwable {
        assertEvaluate(15, "def x=5; x*=3; return x;");
    }

    @Test
    public void divEqual() throws Throwable {
        assertEvaluate(new BigDecimal("10"), "def x=50; x/=5; return x;");
    }

    @Test
    public void instanceOfKeyword() throws Throwable {
        assertEvaluate(false, "null instanceof String");
        assertEvaluate(true, "3 instanceof Integer");
        assertEvaluate(true, "new RuntimeException() instanceof Exception");
        assertEvaluate(true, "'12345' instanceof String");
    }

    @Test public void safeNavigation() throws Throwable {
        assertEvaluate(null, "def x = null; x?.stuff()");
        assertEvaluate(null, "def x = null; x?.stuff");
        assertEvaluate(null, "def x = null; x?.@stuff");
    }

    @Test
    public void compoundBitwiseAssignment() throws Throwable {
        for (int x : new int[] { 0, 1, 2, 3, 4 }) {
            for (int y : new int[] { 0, 1, 2, 3, 4 }) {
                assertEvaluate(x & y, "def x=" + x + "; x&=" + y + "; return x;");
                assertEvaluate(x | y, "def x=" + x + "; x|=" + y + "; return x;");
                assertEvaluate(x ^ y, "def x=" + x + "; x^=" + y + "; return x;");
            }
        }
    }

    @Test
    public void attributeSet() throws Throwable {
        assertEvaluate(1, "new java.awt.Point(1,2).@x");
    }

    @Test
    public void attributeGet() throws Throwable {
        assertEvaluate(6, "def p = new java.awt.Point(1,2); p.@x+=5; p.@x");
    }

    @Test
    public void multidimensionalArrayInstantiation() throws Throwable {
        assertEvaluate(12,
            "def x = new int[3][4];\n" +
            "int z = 0;\n" +
            "for (int i=0; i<x.length; i++)\n" +
            "    z += x[i].length;\n" +
            "return z;\n");
    }

    @Test
    public void arrayAccess() throws Throwable {
        assertEvaluate(7, "def x = new int[3]; x[0]=1; x[1]=x[0]+2; x[1]+=4; return x[1]");
    }

    @Test
    public void bitShift() throws Throwable {
        assertEvaluate(3 * 8, "3<<3");
        assertEvaluate(3 * 8, "x=3; x<<=3; x");
        assertEvaluate(5 / 2, "5 >> 1");
        assertEvaluate(5 / 2, "x=5; x>>=1; x");
        assertEvaluate(2147483647, "-1>>>1");
        assertEvaluate(2147483647, "x=-1; x>>>=1; x");

        assertEvaluate(List.of("hello", "world"), "x=[]; x<<'hello'; x<<'world'; x");
    }

    @Test
    public void inOperator() throws Throwable {
        assertEvaluate(true, "3 in [1,2,3]");
        assertEvaluate(true, "'ascii' in String.class");
        assertEvaluate(false, "6 in [1,2,3]");
        assertEvaluate(false, "'ascii' in URL.class");
    }

    @Test
    public void regexpOperator() throws Throwable {
        assertEvaluate(true, "('cheesecheese' =~ 'cheese') as boolean");
        assertEvaluate(true, "('cheesecheese' =~ /cheese/) as boolean");
        assertEvaluate(false, "('cheese' =~ /ham/) as boolean");

        assertEvaluate(true, "('2009' ==~ /\\d+/) as boolean");
        assertEvaluate(false, "('holla' ==~ /\\d+/) as boolean");
    }

    @Issue("JENKINS-32062")
    @Test
    public void arrayPassedToMethod() throws Throwable {
        assertEvaluate(4, "def m(x) {x.size()}; def a = [1, 2]; a.size() + m(a)"); // control case
        assertEvaluate(4, "def m(x) {x.size()}; def a = [1, 2].toArray(); a.length + m(Arrays.asList(a))"); // workaround #1
        assertEvaluate(4, "@NonCPS def m(x) {x.length}; def a = [1, 2].toArray(); a.length + m(a)"); // workaround #2
        assertEvaluate(4, "def m(x) {x.length}; def a = [1, 2].toArray(); a.length + m(a)"); // formerly: groovy.lang.MissingPropertyException: No such property: length for class: java.lang.Integer
    }

    @Issue("JENKINS-27893")
    @Test
    public void varArgs() throws Throwable {
        assertEvaluate(1, "def fn(String... args) { args.size() }; fn('one string')");
    }

    @Issue("JENKINS-28277")
    @Test
    public void currying() throws Throwable {
        assertEvaluate("foofoo", "def nCopies = { int n, String str -> str*n }; def twice=nCopies.curry(2); twice('foo')");
    }

    @Issue("JENKINS-28277")
    @Test
    public void ncurrying_native_closure() throws Throwable {
        assertEvaluate(List.of(-3, 2),
            "@NonCPS\n" +
            "def makeNativeClosure() {\n" +
            "    Collections.&binarySearch\n" +
            "}\n" +
            "def catSearcher = makeNativeClosure().ncurry(1,'cat')\n" +
            "return [\n" +
            "    catSearcher(['ant','bee','dog']),\n" +
            "    catSearcher(['ant','bee','cat'])\n" +
            "]\n");
    }

    @Test
    public void fieldDirect() throws Throwable {
        assertEvaluate(33, "class C {private int x = 33}; new C().x");
    }

    @Issue("JENKINS-31484")
    @Test
    public void fieldViaGetter() throws Throwable {
        assertEvaluate(66, "class C {private int x = 33; int getX() {2 * this.@x}}; new C().x");
        assertEvaluate(66, "class C {private int x = 33; int getX() {2 * x}}; new C().x");
    }

    @Issue("JENKINS-31484")
    @Ignore("Currently throws StackOverflowError")
    @Test
    public void fieldViaGetterWithThis() throws Throwable {
        assertEvaluate(66, "class C {private int x = 33; int getX() {2 * this.x}}; new C().x");
    }

    @Issue("JENKINS-31484")
    @Test
    public void fieldViaSetter() throws Throwable {
        assertEvaluate(22, "class C {private int x = 0; int getX() {2 * x}; void setX(int x) {this.@x = x / 3}}; C c = new C(); c.x = 33; c.x");
        assertEvaluate(22, "class C {private int x = 0; int getX() {2 * x}; void setX(int x) {this.x = x / 3}}; C c = new C(); c.x = 33; c.x");
    }

    @Test
    public void nonField() throws Throwable {
        assertEvaluate(new BigDecimal("22"), "class C extends HashMap {def read() {x * 2}; def write(x) {this.x = x / 3}}; C c = new C(); c.write(33); c.read()");
    }

    @Test
    public void method_pointer() throws Throwable {
        // method pointer to a native static method
        assertEvaluate(List.of(3, 7),
            "def add = CpsTransformerTest.&add\n" +
            "return [ add(1,2), add(3,4) ]\n");

        // method pointer to a native instance method
        assertEvaluate(List.of(true, false),
            "def contains = 'foobar'.&contains\n" +
            "return [ contains('oo'), contains('xyz') ]\n");

        // method pointer to a CPS transformed method
        assertEvaluate(List.of(1101, 10011),
            "class X {\n" +
            "    int z;\n" +
            "    X(int z) { this.z = z; }\n" +
            "    int add(int x, int y) { x+y+z }\n" +
            "}\n" +
            "def adder = (new X(1)).&add;\n" +
            "def plus1 = adder.curry(10)\n" +
            "return [ adder(100,1000), plus1(10000) ]\n");
    }

    public static int add(int a, int b) { return a+b; }

    @Issue("https://github.com/cloudbees/groovy-cps/issues/26")
    @Test
    public void interfaceDeclaration() throws Throwable {
        assertEvaluate(true,
            "interface Strategy {\n" +
            "    Closure process(Object event)\n" +
            "}\n" +
            "return true\n");
    }

    @Issue("https://github.com/cloudbees/groovy-cps/issues/26")
    @Test
    public void emptyInterfaceDeclaration() throws Throwable {
        assertEvaluate(true,
            "interface Empty {}\n" +
            "return true\n");
    }

    @Issue("JENKINS-44280")
    @Ignore
    @Test
    public void overloadedMethods() throws Throwable {
        assertEvaluate("iterable",
            "public String bar(List<String> l) {\n" +
            "    return bar((Iterable)l)\n" +
            "}\n" +
            "public String bar(Iterable<String> l) {\n" +
            "    return 'iterable'\n" +
            "}\n" +
            "List<String> s = ['a', 'b']\n" +
            "return bar(s)\n");
    }

    @Issue("JENKINS-44280")
    @Ignore
    @Test
    public void overloadedMethodsWithRawTypes() throws Throwable {
        assertEvaluate("iterable",
            "public String bar(List l) {\n" +
            "    return bar((Iterable)l)\n" +
            "}\n" +
            "public String bar(Iterable l) {\n" +
            "    return 'iterable'\n" +
            "}\n" +
            "List s = ['a', 'b']\n" +
            "return bar(s)\n");
    }

    @Issue("JENKINS-44280")
    @Ignore
    @Test
    public void overloadedStaticMethods() throws Throwable {
        assertEvaluate("iterable",
            "public static String bar(List l) {\n" +
            "    return bar((Iterable)l)\n" +
            "}\n" +
            "public static String bar(Iterable l) {\n" +
            "    return 'iterable'\n" +
            "}\n" +
            "List s = ['a', 'b']\n" +
            "return bar(s)\n");
    }

    public static class Base {
        @Override
        public String toString() {
            return "base";
        }
    }

    @Test
    public void superClass() throws Throwable {
        assertEvaluate("xbase",
            "class Foo extends CpsTransformerTest.Base {\n" +
            "    public String toString() {\n" +
            "        return 'x'+super.toString();\n" +
            "    }\n" +
            "}\n" +
            "new Foo().toString();\n");
    }

    @Issue("JENKINS-45982")
    @Test
    public void transformedSuperClass() throws Throwable {
        assertEvaluate("ybase",
            "class Foo extends CpsTransformerTest.Base {\n" +
            "    public String other() {\n" +
            "        return 'base'\n" +
            "    }\n" +
            "}\n" +
            "class Bar extends Foo {\n" +
            "    public String other() {\n" +
            "        return 'y'+super.other()\n" +
            "    }\n" +
            "}\n" +
            "new Bar().other();\n");
    }

    @Issue("JENKINS-52395")
    @Test
    public void transformedSuperSuperClass() throws Throwable {
        assertEvaluate("zybase",
            "class Foo extends CpsTransformerTest.Base {\n" +
            "    public String other() {\n" +
            "        return 'base'\n" +
            "    }\n" +
            "}\n" +
            "class Bar extends Foo {\n" +
            "    public String other() {\n" +
            "        return 'y'+super.other()\n" +
            "    }\n" +
            "}\n" +
            "class Baz extends Bar {\n" +
            "    public String other() {\n" +
            "        return 'z'+super.other()\n" +
            "    }\n" +
            "}\n" +
            "new Baz().other();\n");
    }

    @Test
    @Issue("https://github.com/cloudbees/groovy-cps/issues/42")
    public void abstractMethod() throws Throwable {
        assertEvaluate(123,
            "abstract class Foo {\n" +
            "    abstract int val()\n" +
            "}\n" +
            "Foo foo = new Foo() {int val() {123}}\n" +
            "foo.val()\n");
    }

    @Issue("https://github.com/cloudbees/groovy-cps/issues/28")
    @Test
    @Ignore
    public void rehydrateClosure() throws Throwable {
        assertEvaluate("from Script instance",
            "class MyStrategy {\n" +
            "    Closure<String> process() {\n" +
            "        return {\n" +
            "            speak()\n" +
            "        }\n" +
            "    }\n" +
            "}\n" +
            "String speak() {\n" +
            "    'from Script instance'\n" +
            "}\n" +
            "Closure<String> closure = new MyStrategy().process()\n" +
            "closure.rehydrate(this, this, this).call()\n");
    }

    @Issue("https://github.com/cloudbees/groovy-cps/issues/16")
    @Test
    @Ignore("cannot easily be supported")
    public void category() throws Throwable {
        assertEvaluate("FOO",
            "class BarCategory {\n" +
            "    static String up(String text) {\n" +
            "        text.toUpperCase()\n" +
            "    }\n" +
            "}\n" +
            "return use(BarCategory) {\n" +
            "    'foo'.up()\n" +
            "};\n");
    }

    @Issue("JENKINS-38268")
    @Test
    public void lexicalScope() throws Throwable {
        assertEvaluate(List.of(1, 1),
            "def a = [id: 'a', count: 0]\n" +
            "def b = [id: 'b', count: 0]\n" +
            "def toRun = [a, b].collect { thing -> return { thing.count = thing.count + 1 } }\n" +
            "toRun.each { arg -> arg() }\n" +
            "return [a.count, b.count]\n");
    }

    @Issue("SECURITY-567")
    @Test
    public void methodPointer() throws Throwable {
        assertEvaluate("baseClass",
            "def b = new CpsTransformerTest.Base()\n" +
            "return (b.&toString)() + (String.getClass().&getSimpleName)()\n");
    }

    @Issue("JENKINS-32213")
    @Test
    public void allClassesSerializable() throws Throwable {
        evalCPSonly("class C {}; def c = new C(); assert c instanceof Serializable");
        evalCPSonly("class C implements Serializable {}; def c = new C(); assert c instanceof Serializable");
        assertTrue((boolean)evalCPSonly(
            "@NonCPS\n" +
            "def anonymousClass() {\n" +
            "  def r = new Runnable() {\n" +
            "    @Override\n" +
            "    public void run() {}\n" +
            "  }\n" +
            "  return r instanceof Serializable\n" +
            "}\n" +
            "return anonymousClass()\n"));
    }

    @Issue("JENKINS-44027")
    @Test
    public void multipleAssignment() throws Throwable {
        assertEvaluate("firstsecondthirdfourth",
            "def (a, b) = ['first', 'second']\n" +
            "def c, d\n" +
            "(c, d) = ['third', 'fourth']\n" +
            "return a + b + c + d\n");
    }

    @Issue("JENKINS-44027")
    @Test
    public void nonArrayLikeMultipleAssignment() throws Throwable {
        assertEvaluate(true,
            "try {\n" +
            "  def (a,b) = 4\n" +
            "  return false\n" +
            "} catch (Exception e) {\n" +
            "  return e instanceof MissingMethodException\n" +
            "}\n");
    }

    @Issue("JENKINS-44027")
    @Test
    public void arrayLikeMultipleAssignment() throws Throwable {
        assertEvaluate("wh",
            "def (a,b) = 'what'\n" +
            "return a + b\n");
    }

    @Issue("JENKINS-44027")
    @Test
    public void mismatchedSizeMultipleAssignment() throws Throwable {
        assertEvaluate("first second fourth null",
            "def (a, b) = ['first', 'second', 'third']\n" +
            "def (c, d) = ['fourth']\n" +
            "return [a, b, c, d].join(' ')\n");
    }

    @Issue("JENKINS-47363")
    @Test
    public void excessiveListElements() throws Throwable {
        String s1 = IntStream.range(0, 250).boxed().map(Object::toString).collect(Collectors.joining(",\n"));
        assertEvaluate(250,
            "def b = [" + s1 + "]\n" +
            "return b.size()\n");

        String s2 = IntStream.range(0, 251).boxed().map(Object::toString).collect(Collectors.joining(",\n"));
        evalCps(
            "def b = [" + s2 + "]\n" +
            "return b.size()\n",
            ShouldFail.class,
            t -> {
                assertThat(t, instanceOf(MultipleCompilationErrorsException.class));
                assertThat(t.getMessage(), containsString("List expressions can only contain up to 250 elements"));
            });
    }

    @Issue("JENKINS-47363")
    @Test
    public void excessiveMapElements() throws Throwable {
        String s1 = IntStream.range(0, 125).boxed().map(i -> i + ":" + i).collect(Collectors.joining(",\n"));
        assertEvaluate(125,
            "def b = [" + s1 + "]\n" +
            "return b.size()\n");
        String s2 = IntStream.range(0, 126).boxed().map(i -> i + ":" + i).collect(Collectors.joining(",\n"));
        evalCps(
            "def b = [" + s2 + "]\n" +
            "return b.size()\n",
            ShouldFail.class,
            t -> {
                assertThat(t, instanceOf(MultipleCompilationErrorsException.class));
                assertThat(t.getMessage(), containsString("Map expressions can only contain up to 125 entries"));
            });
    }

    @Issue("JENKINS-49679")
    @Test
    public void multipleAssignmentRunsMethodOnce() throws Throwable {
        assertEvaluate("firstsecondthirdfourth",
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
            "return a + b + c + d\n");
    }

    @Test
    public void mapEntryInBadContext() throws Throwable {
        evalCps("return [[a: 'a'], [b: 'b'][c: 'c']]", ShouldFail.class, e -> {
            ec.checkThat(e, instanceOf(MultipleCompilationErrorsException.class));
            ec.checkThat(e.getMessage(), containsString("Unsupported map entry expression for CPS transformation in this context"));
        });
    }

    @Test
    public void spreadMethodCall() throws Throwable {
        evalCps("return ['a', 'b', 'c']*.hashCode()", ShouldFail.class, e -> {
            ec.checkThat(e, instanceOf(MultipleCompilationErrorsException.class));
            ec.checkThat(e.getMessage(), containsString("spread not yet supported for CPS transformation"));
        });
    }

    @Test
    public void synchronizedStatement() throws Throwable {
        evalCps("synchronized(this) { return 1 }", ShouldFail.class, e -> {
            ec.checkThat(e, instanceOf(MultipleCompilationErrorsException.class));
            ec.checkThat(e.getMessage(), containsString("synchronized is unsupported for CPS transformation"));
        });
    }

    @Issue("JENKINS-46163")
    @Test public void spreadExpression() throws Throwable {
        String[] declarations = new String[] {
            "def", // ArrayList
            "Object[]", // Object array
            "int[]" // Primitive array
        };
        for (String decl : declarations) {
            assertEvaluate(List.of(1, 2, 3, 4, 5),
                decl + " x = [1, 2, 3]\n" +
                "return [*x, 4, 5]\n");
            assertEvaluate(List.of(4, 1, 2, 3, 5),
                decl + " x = [1, 2, 3]\n" +
                "return [4, *x, 5]\n");
            assertEvaluate(List.of(4, 5, 1, 2, 3),
                decl + " x = [1, 2, 3]\n" +
                "return [4, 5, *x]\n");
            assertEvaluate(List.of(1, 2, 3),
                decl + " x = [1, 2, 3]\n" +
                "return [*x]\n");
            assertEvaluate(List.of(1, 2, 3, 4, 5, 6, 7),
                decl + " x = [2, 3]\n" +
                decl + " y = [5, 6]\n" +
                "return [1, *x, 4, *y, 7]\n");
        }
        assertEvaluate(Collections.singletonList(null),
                "def x = null\n" +
                "return [*x]\n");
        assertFailsWithSameException(
                "def x = 1\n" +
                "return *x\n"); // *x cannot exist outside of list literals and method call arguments.
    }

    @Issue("JENKINS-46163")
    @Test public void spreadMethodCallArguments() throws Throwable {
        String[] declarations = new String[] {
            "def", // ArrayList
            "Object[]", // Object array
            "int[]" // Primitive array
        };
        for (String decl : declarations) {
            assertEvaluate(List.of(1, 2, 3),
                decl + " x = [1, 2, 3]\n" +
                "def id(a, b, c) { [a, b, c] }\n" +
                "return id(*x)\n");
            assertEvaluate(List.of(1, 2, 3),
                decl + " x = [2, 3]\n" +
                "def id(a, b, c) { [a, b, c] }\n" +
                "return id(1, *x)\n");
            assertEvaluate(List.of(1, 2, 3),
                decl + " x = [1, 2]\n" +
                "def id(a, b, c) { [a, b, c] }\n" +
                "return id(*x, 3)\n");
            assertEvaluate(List.of(1, 2, 3),
                decl + " x = [2]\n" +
                "def id(a, b, c) { [a, b, c] }\n" +
                "return id(1, *x, 3)\n");
        }
        assertEvaluate(Arrays.asList(1, null, 3),
                "def x = null\n" +
                "def id(a, b, c) { [a, b, c] }\n" +
                "return id(1, *x, 3)\n");
    }

    @Issue("JENKINS-46163")
    @Test public void spreadMapExpression() throws Throwable {
        assertEvaluate(InvokerHelper.createMap(new Object[] { "a", 1, "b", 2, "c", 3, "d", 4, "e", 5 }),
            "def x = [a: 1, b: 2, c: 3]\n" +
            "return [*:x, d: 4, e: 5]\n");
        assertEvaluate(InvokerHelper.createMap(new Object[] { "d", 4, "a", 1, "b", 2, "c", 3, "e", 5 }),
            "def x = [a: 1, b: 2, c: 3]\n" +
            "return [d: 4, *:x, e: 5]\n");
        assertEvaluate(InvokerHelper.createMap(new Object[] { "d", 4, "e", 5, "a", 1, "b", 2, "c", 3, "e", 5 }),
            "def x = [a: 1, b: 2, c: 3]\n" +
            "return [d: 4, e: 5, *:x]\n");
        assertEvaluate(InvokerHelper.createMap(new Object[] { "a", 1, "b", 2, "c", -1 }),
            "def x = [a: 1, b: 2, c: 3]\n" +
            "return [c: 4, *:x, c: -1]\n"); // The final value for a key takes precedence.
        assertEvaluate(InvokerHelper.createMap(new Object[] { "a", 1, "b", 2, "c", 3 }),
            "def x = [a: 1, b: 2, c: 3]\n" +
            "return [*:x]\n");
        assertEvaluate(InvokerHelper.createMap(new Object[] { "a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7 }),
            "def x = [b: 2, c: 3]\n" +
            "def y = [e: 5, f: 6]\n" +
            "return [a: 1, *:x, d: 4, *:y, g: 7]\n");
        // When used in method call arguments, *:map is the same as map, except for creating an instance of SpreadMap.
        // IDK why Groovy even allows the spread syntax here.
        assertEvaluate(Map.of("a", 1),
            "def x = [a: 1]\n" +
            "def id(def m) { m }\n" +
            "return id(*:x)\n");
        assertFailsWithSameException(
            "def x = [a: 1]\n" +
            "def id(String a, int i) { [a, i] }\n" +
            "return id(*:x)\n");
        assertFailsWithSameException(
            "def x = [a: 1]\n" +
            "return *:x\n"); // *:x is syntactically invalid outside of map literals and method call arguments.
    }

    @Test
    public void initialExpressionsInMethodsAreCpsTransformed() throws Throwable {
        assertEvaluate(Boolean.FALSE,
                "def m1() { true }\n" +
                "def m2(p = m1()){ false }\n" +
                "m2()\n");
    }

    @Test public void methodsWithInitialExpressionsAreExpandedToCorrectOverloads() throws Throwable {
        assertEvaluate(List.of("abc", "xbc", "xyc", "xyz"),
                "def m2(a = 'a', b = 'b', c = 'c') {\n" +
                "    a + b + c\n" +
                "}\n" +
                "def r1 = m2()\n" +
                "def r2 = m2('x')\n" +
                "def r3 = m2('x', 'y')\n" +
                "def r4 = m2('x', 'y', 'z')\n" +
                "[r1, r2, r3, r4]");
        assertEvaluate(List.of("abc", "xbc", "xby"),
                "def m2(a = 'a', b, c = 'c') {\n" +
                "    a + b + c\n" +
                "}\n" +
                "def r1 = m2('b')\n" +
                "def r2 = m2('x', 'b')\n" +
                "def r3 = m2('x', 'b', 'y')\n" +
                "[r1, r2, r3]");
    }

    @Test public void voidMethodsWithInitialExpressionsAreExpandedToCorrectOverloads() throws Throwable {
        assertEvaluate(List.of("abc", "xbc", "xyc", "xyz"),
                "import groovy.transform.Field\n" +
                "@Field def r = []\n" +
                "void m2(a = 'a', b = 'b', c = 'c') {\n" +
                "    r.add(a + b + c)\n" +
                "}\n" +
                "m2()\n" +
                "m2('x')\n" +
                "m2('x', 'y')\n" +
                "m2('x', 'y', 'z')\n" +
                "r");
        assertEvaluate(List.of("abc", "xbc", "xby"),
                "import groovy.transform.Field\n" +
                "@Field def r = []\n" +
                "void m2(a = 'a', b, c = 'c') {\n" +
                "    r.add(a + b + c)\n" +
                "}\n" +
                "m2('b')\n" +
                "m2('x', 'b')\n" +
                "m2('x', 'b', 'y')\n" +
                "r");
    }

    @Issue("JENKINS-57253")
    @Test public void illegalBreakStatement() throws Throwable {
        getBinding().setProperty("sentinel", 1);
        evalCps("sentinel = 2; break;", ShouldFail.class, e -> {
            assertThat(e.toString(), containsString("the break statement is only allowed inside loops or switches"));
        });
        assertEquals("Script should fail during compilation", 1, getBinding().getProperty("sentinel"));
    }

    @Ignore("groovy-cps does not cast method return values to the declared type")
    @Test public void methodReturnValuesShouldBeCastToDeclaredReturnType() throws Throwable {
        assertEvaluate(true,
                "Boolean castToBoolean(def o) { o }\n" +
                "castToBoolean(123)\n");
    }

    @Test public void castToTypeShouldBeUsedForImplicitCasts() throws Throwable {
        assertEvaluate(List.of("toString", "toString", "toString", "asType"),
                "class Test {\n" +
                "  def auditLog = []\n" +
                "  @NonCPS\n" +
                "  def asType(Class c) {\n" +
                "    auditLog.add('asType')\n" +
                "    'Test.asType'\n" +
                "  }\n" +
                "  @NonCPS\n" +
                "  String toString() {\n" +
                "    auditLog.add('toString')\n" +
                "    'Test.toString'\n" +
                "  }\n" +
                "}\n" +
                "Test t = new Test()\n" +
                "String variable = t\n" +
                "String[] array = [t]\n" +
                "(String)t\n" +
                "t as String\n" + // This is the only cast that should call asType.
                "t.auditLog\n");
    }

    @Test public void castRelatedMethodsShouldBeNonCps() throws Throwable {
        // asType CPS (supported (to the extent possible) for compatibility with existing code)
        assertEvaluate(List.of(false, "asType class java.lang.Boolean"),
                "class Test {\n" +
                "  def auditLog = []\n" +
                "  def asType(Class c) {\n" +
                "    auditLog.add('asType ' + c)\n" +
                "    false\n" +
                "  }\n" +
                "}\n" +
                "def t = new Test()\n" +
                "[t as Boolean, t.auditLog[0]]");
        // asType NonCPS (preferred)
        assertEvaluate(List.of("asType class java.lang.Boolean"),
                "class Test {\n" +
                "  def auditLog = []\n" +
                "  @NonCPS\n" +
                "  def asType(Class c) {\n" +
                "    auditLog.add('asType ' + c)\n" +
                "    null\n" +
                "  }\n" +
                "}\n" +
                "def t = new Test()\n" +
                "t as Boolean\n" +
                "t.auditLog");
        // asBoolean CPS (has never worked, still does not work)
        evalCps(
                "class Test {\n" +
                "  def auditLog = []\n" +
                "  def asBoolean() {\n" +
                "    auditLog.add('asBoolean')\n" +
                "  }\n" +
                "}\n" +
                "def t = new Test()\n" +
                "(Boolean)t\n" +
                "t.auditLog",
                ShouldFail.class,
                t -> {
                    ec.checkThat(t.toString(), equalTo("java.lang.IllegalStateException: Test.asBoolean must be @NonCPS; see: https://jenkins.io/redirect/pipeline-cps-method-mismatches/"));
                });
        // asBoolean NonCPS (required)
        assertEvaluate(List.of("asBoolean"),
                "class Test {\n" +
                "  def auditLog = []\n" +
                "  @NonCPS\n" +
                "  def asBoolean() {\n" +
                "    auditLog.add('asBoolean')\n" +
                "  }\n" +
                "}\n" +
                "def t = new Test()\n" +
                "(Boolean)t\n" +
                "t.auditLog");
    }

    @Test
    public void enums() throws Throwable {
        assertEvaluate("FIRST",
                "enum EnumTest { FIRST, SECOND }; EnumTest.FIRST.toString()");
        assertEvaluate("FIRST",
                "enum EnumTest { FIRST(), SECOND(); EnumTest() { } }; EnumTest.FIRST.toString()");
    }

    @Test
    public void anonymousClass() throws Throwable {
        assertEvaluate(6,
                "def o = new Object() { def plusOne(x) { x + 1 } }\n" +
                "o.plusOne(5)");
    }

    @Issue("JENKINS-62064")
    @Test public void assignmentExprsEvalToRHS() throws Throwable {
        assertEvaluate(List.of(1, 1, 1),
                "def a = b = c = 1\n" +
                "[a, b, c]\n");
        assertEvaluate(List.of(2, 3, 4),
                "def a = b = c = 1\n" +
                "c += b += a += 1\n" +
                "[a, b, c]\n");
    }
}
