package com.cloudbees.groovy.cps;

import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import groovy.lang.IntRange;
import java.io.File;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.codehaus.groovy.control.MultipleCompilationErrorsException;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

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
        for (int i : new int[] {1, 2, 3}) {
            for (int j : new int[] {1, 2, 3}) {
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
        assertEvaluate("2.0.2", """
                                x=0;
                                y = x++;
                                z = ++x;
                                return x+'.'+y+'.'+z;
                                """);
    }

    @Test
    public void decrement() throws Throwable {
        assertEvaluate("3.5.3", """
                                x=5;
                                y = x--;
                                z = --x;
                                return x+'.'+y+'.'+z;
                                """);
    }

    @Test
    public void break_() throws Throwable {
        assertEvaluate(0, """
                          x=0;
                          int i=0;
                          for (i=0; i<5; i+=1) {
                              break;
                              x+=1;
                          }
                          return i+x;
                          """);
    }

    @Test
    public void globalBreak_() throws Throwable {
        assertEvaluate("0.0.0", """
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
                                return i+'.'+j+'.'+x;
                                """);
    }

    @Test
    public void functionCall() throws Throwable {
        assertEvaluate(3, """
                          int i=1;
                          i.plus(2)""");
    }

    @Test
    public void functionCall0arg() throws Throwable {
        assertEvaluate("123", "123.toString()");
    }

    @Test
    public void constructorCall() throws Throwable {
        assertEvaluate("abcdef", "new String('abc'+'def')");
    }

    @Test
    public void constructorCall0arg() throws Throwable {
        assertEvaluate("", "new String()");
    }

    @Issue("https://github.com/cloudbees/groovy-cps/issues/31")
    @Test
    public void constructorList() throws Throwable {
        assertEvaluate(new File("/parent", "name"), """
                                                    File f = ['/parent', 'name']
                                                    return f""");

        // Test the closure env
        assertEvaluate(new File("/parent", "name"), """
                                                    def close = {String parent, String name -> [parent, name] as File}
                                                    return close('/parent', 'name')
                                                    """);
    }

    @Test
    public void workflowCallingWorkflow() throws Throwable {
        assertEvaluate(55, """
                def fib(int x) {
                  if (x==0)     return 0;
                  if (x==1)     return 1;
                  // assignment to make sure x is treated as local variable
                  x = fib(x-1)+fib(x-2);
                  return x;
                }
                fib(10);
                """);
    }

    @Test
    public void typeCoercion() throws Throwable {
        assertEvaluate(Locale.getAvailableLocales(), """
                                                     interface I {
                                                         Locale[] getAvailableLocales()
                                                     }
                                                     try {
                                                         (Locale as I).getAvailableLocales()
                                                     } catch (e) {
                                                         e.toString()
                                                     }
                                                     """);
    }

    /**
     *
     */
    @Test
    public void exceptionFromNonCpsCodeShouldBeCaughtByCatchBlockInCpsCode() throws Throwable {
        String message = (String) evalCPSonly("""
                                              def foo() {
                                                'abc'.substring(5);
                                                // will caught exception
                                                return 'fail';
                                              }
                                              try {
                                                return foo();
                                              } catch(StringIndexOutOfBoundsException e) {
                                                return e.message;
                                              }
                                              """);
        assertThat(
                message,
                anyOf(
                        equalTo("String index out of range: -2"), // Before Java 14
                        equalTo("begin 5, end 3, length 3"), // Before Java 18
                        equalTo("Range [5, 3) out of bounds for length 3"))); // Later versions
    }

    /**
     * while loop that evaluates to false and doesn't go through the body
     */
    @Test
    public void whileLoop() throws Throwable {
        assertEvaluate(1, """
                          int x=1;
                          while (false) {
                              x++;
                          }
                          return x;
                          """);
    }

    /**
     * while loop that goes through several iterations.
     */
    @Test
    public void whileLoop5() throws Throwable {
        assertEvaluate(5, """
                          int x=1;
                          while (x<5) {
                              x++;
                          }
                          return x;
                          """);
    }

    /**
     * do-while loop that evaluates to false immediately
     */
    @Test
    @Ignore("Groovy 2.x does not support do-while loops")
    public void doWhileLoop() throws Throwable {
        assertEvaluate(2, """
                          int x=1;
                          do {
                              x++;
                          } while (false);
                          return x;
                          """);
    }

    /**
     * do/while loop that goes through several iterations.
     */
    @Test
    @Ignore("Groovy 2.x does not support do-while loops")
    public void dowhileLoop5() throws Throwable {
        assertEvaluate(5, """
                          int x=1;
                          do {
                              x++;
                          } while (x<5);
                          return x;
                          """);
    }

    @Test
    public void helloClosure() throws Throwable {
        assertEvaluate(5, """
                          x = { -> 5 }
                          return x();
                          """);
    }

    @Test
    public void closureShouldCaptureLiveVariables() throws Throwable {
        assertEvaluate("0.3.5", """
                                def c1,c2;
                                { ->
                                    def x = 0;
                                    c1 = { return x; }
                                    c2 = { v -> x=v; }
                                }();
                                r = ''+c1();
                                c2(3);
                                r += '.'+c1();
                                c2(5);
                                r += '.'+c1();
                                return r;
                                """);
    }

    @Test
    public void closureHasImplicitItVariable() throws Throwable {
        assertEvaluate(4, """
                          c = { it+1 }
                          c(3);
                          """);
    }

    /**
     * A common pattern of using closure as a configuration block requires
     * a library to set a delegate.
     */
    @Test
    public void closureDelegateProperty() throws Throwable {
        assertEvaluate("3-xyz-xyz-true", """
                                         def config(c) {
                                             def map = [:];
                                             c.resolveStrategy = Closure.DELEGATE_FIRST;
                                             c.delegate = map;
                                             c();
                                             return map;
                                         }
                                         def x = config {
                                             foo = 3;
                                             bar = 'xyz';
                                             zot = bar;
                                             fog = containsKey('foo');
                                         }
                                         return [x.foo, x.bar, x.zot, x.fog].join('-');
                                         """);
    }

    @Test
    public void serialization() throws Throwable {
        CpsCallableInvocation s = parseCps("""
                                           def plus3(int x) {
                                               return x+3;
                                           }
                                           for (int x=0; x<10; x++) {
                                            // meaningless code to cram as much coding construct as possible
                                               try {
                                                   while (false)
                                                       ;
                                               } catch(Exception e) {
                                                   ;
                                               }
                                           }
                                           1+plus3(3*2)
                                           """);
        Continuable cx = new Continuable(s.invoke(null, null, Continuation.HALT));
        cx = roundtripSerialization(cx);
        assertEquals(10, cx.run(null));
    }

    @Test
    public void assertion() throws Throwable {
        // when assertion passes
        assertEvaluate(3, """
                          assert true
                          assert true : 'message'
                          return 3;
                          """);

        evalCps("assert 1+2 == ((4));", ShouldFail.class, t -> {
            ec.checkThat(t, instanceOf(AssertionError.class));
            ec.checkThat(t.getMessage(), containsString("1+2 == ((4))"));
        });

        evalCps("assert (1+2) == 4 : 'with message';", ShouldFail.class, t -> {
            ec.checkThat(t, instanceOf(AssertionError.class));
            ec.checkThat(
                    t.getMessage(), containsString("with message. Expression: assert (1+2) == 4 : 'with message'"));
        });
    }

    @Test
    public void unaryOps() throws Throwable {
        assertEvaluate(0, """
                          def x = 5;
                          def y = -x;
                          def z = +x;
                          return y+z;
                          """);
    }

    @Test
    public void not() throws Throwable {
        assertEvaluate("y=false,z=true", """
                                         def x = true;
                                         def y = !x;
                                         def z = !y;
                                         return 'y='+y+',z='+z;
                                         """);
    }

    @Test
    public void bitwiseNegative() throws Throwable {
        assertEvaluate(-33, """
                            int x = 32;
                            return ~x;
                            """);
    }

    @Test
    public void gstring() throws Throwable {
        assertEvaluate("hello 4=foo", """
                                      def x = 'foo';
                                      return "hello ${1+3}=${x}".toString()
                                      """);
    }

    @Issue("https://github.com/cloudbees/groovy-cps/issues/15")
    @Test
    public void gstringWithStringWriterClosure() throws Throwable {
        String script = """
                String text = 'Foobar';
                String result = /${ w -> w << text}/.toString();
                return result;
                """;
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

    @Test
    public void logicalOp() throws Throwable {
        assertEvaluate(false, "true && (false || false)");
        assertEvaluate(true, "true && (true || false)");
        assertEvaluate(false, "false && (true || false)");
        assertEvaluate(true, "false || 'rhs of || must be cast to boolean'");
        assertEvaluate(true, "true && 'rhs of && must be cast to boolean'");
        assertEvaluate("[true, false, true, true] [1, 0, 0, 4]", """
                                                                 x = [0, 0, 0, 0]
                                                                 def set(index) {
                                                                     x[index - 1] = index
                                                                     true
                                                                 }
                                                                 def r = [
                                                                     true  && set(1),
                                                                     false && set(2),
                                                                     true  || set(3),
                                                                     false || set(4)
                                                                 ]
                                                                 "${r} ${x}".toString()
                                                                 """);
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

    @Test
    public void safeNavigation() throws Throwable {
        assertEvaluate(null, "def x = null; x?.stuff()");
        assertEvaluate(null, "def x = null; x?.stuff");
        assertEvaluate(null, "def x = null; x?.@stuff");
    }

    @Test
    public void compoundBitwiseAssignment() throws Throwable {
        for (int x : new int[] {0, 1, 2, 3, 4}) {
            for (int y : new int[] {0, 1, 2, 3, 4}) {
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
        assertEvaluate(12, """
                           def x = new int[3][4];
                           int z = 0;
                           for (int i=0; i<x.length; i++)
                               z += x[i].length;
                           return z;
                           """);
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
        assertEvaluate(
                4, "def m(x) {x.size()}; def a = [1, 2].toArray(); a.length + m(Arrays.asList(a))"); // workaround #1
        assertEvaluate(4, "@NonCPS def m(x) {x.length}; def a = [1, 2].toArray(); a.length + m(a)"); // workaround #2
        assertEvaluate(4, "def m(x) {x.length}; def a = [1, 2].toArray(); a.length + m(a)"); // formerly:
        // groovy.lang.MissingPropertyException: No such property: length for class: java.lang.Integer
    }

    @Issue("JENKINS-27893")
    @Test
    public void varArgs() throws Throwable {
        assertEvaluate(1, "def fn(String... args) { args.size() }; fn('one string')");
    }

    @Issue("JENKINS-28277")
    @Test
    public void currying() throws Throwable {
        assertEvaluate(
                "foofoo", "def nCopies = { int n, String str -> str*n }; def twice=nCopies.curry(2); twice('foo')");
    }

    @Issue("JENKINS-28277")
    @Test
    public void ncurrying_native_closure() throws Throwable {
        assertEvaluate(List.of(-3, 2), """
                                       @NonCPS
                                       def makeNativeClosure() {
                                           Collections.&binarySearch
                                       }
                                       def catSearcher = makeNativeClosure().ncurry(1,'cat')
                                       return [
                                           catSearcher(['ant','bee','dog']),
                                           catSearcher(['ant','bee','cat'])
                                       ]
                                       """);
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
        assertEvaluate(
                22,
                "class C {private int x = 0; int getX() {2 * x}; void setX(int x) {this.@x = x / 3}}; C c = new C(); c.x = 33; c.x");
        assertEvaluate(
                22,
                "class C {private int x = 0; int getX() {2 * x}; void setX(int x) {this.x = x / 3}}; C c = new C(); c.x = 33; c.x");
    }

    @Test
    public void nonField() throws Throwable {
        assertEvaluate(
                new BigDecimal("22"),
                "class C extends HashMap {def read() {x * 2}; def write(x) {this.x = x / 3}}; C c = new C(); c.write(33); c.read()");
    }

    @Test
    public void method_pointer() throws Throwable {
        // method pointer to a native static method
        assertEvaluate(List.of(3, 7), """
                                      def add = CpsTransformerTest.&add
                                      return [ add(1,2), add(3,4) ]
                                      """);

        // method pointer to a native instance method
        assertEvaluate(List.of(true, false), """
                                             def contains = 'foobar'.&contains
                                             return [ contains('oo'), contains('xyz') ]
                                             """);

        // method pointer to a CPS transformed method
        assertEvaluate(List.of(1101, 10011), """
                                             class X {
                                                 int z;
                                                 X(int z) { this.z = z; }
                                                 int add(int x, int y) { x+y+z }
                                             }
                                             def adder = (new X(1)).&add;
                                             def plus1 = adder.curry(10)
                                             return [ adder(100,1000), plus1(10000) ]
                                             """);
    }

    public static int add(int a, int b) {
        return a + b;
    }

    @Issue("https://github.com/cloudbees/groovy-cps/issues/26")
    @Test
    public void interfaceDeclaration() throws Throwable {
        assertEvaluate(true, """
                             interface Strategy {
                                 Closure process(Object event)
                             }
                             return true
                             """);
    }

    @Issue("https://github.com/cloudbees/groovy-cps/issues/26")
    @Test
    public void emptyInterfaceDeclaration() throws Throwable {
        assertEvaluate(true, """
                             interface Empty {}
                             return true
                             """);
    }

    @Issue("JENKINS-44280")
    @Ignore
    @Test
    public void overloadedMethods() throws Throwable {
        assertEvaluate("iterable", """
                                   public String bar(List<String> l) {
                                       return bar((Iterable)l)
                                   }
                                   public String bar(Iterable<String> l) {
                                       return 'iterable'
                                   }
                                   List<String> s = ['a', 'b']
                                   return bar(s)
                                   """);
    }

    @Issue("JENKINS-44280")
    @Ignore
    @Test
    public void overloadedMethodsWithRawTypes() throws Throwable {
        assertEvaluate("iterable", """
                                   public String bar(List l) {
                                       return bar((Iterable)l)
                                   }
                                   public String bar(Iterable l) {
                                       return 'iterable'
                                   }
                                   List s = ['a', 'b']
                                   return bar(s)
                                   """);
    }

    @Issue("JENKINS-44280")
    @Ignore
    @Test
    public void overloadedStaticMethods() throws Throwable {
        assertEvaluate("iterable", """
                                   public static String bar(List l) {
                                       return bar((Iterable)l)
                                   }
                                   public static String bar(Iterable l) {
                                       return 'iterable'
                                   }
                                   List s = ['a', 'b']
                                   return bar(s)
                                   """);
    }

    public static class Base {
        @Override
        public String toString() {
            return "base";
        }
    }

    @Test
    public void superClass() throws Throwable {
        assertEvaluate("xbase", """
                                class Foo extends CpsTransformerTest.Base {
                                    public String toString() {
                                        return 'x'+super.toString();
                                    }
                                }
                                new Foo().toString();
                                """);
    }

    @Issue("JENKINS-45982")
    @Test
    public void transformedSuperClass() throws Throwable {
        assertEvaluate("ybase", """
                                class Foo extends CpsTransformerTest.Base {
                                    public String other() {
                                        return 'base'
                                    }
                                }
                                class Bar extends Foo {
                                    public String other() {
                                        return 'y'+super.other()
                                    }
                                }
                                new Bar().other();
                                """);
    }

    @Issue("JENKINS-52395")
    @Test
    public void transformedSuperSuperClass() throws Throwable {
        assertEvaluate("zybase", """
                                 class Foo extends CpsTransformerTest.Base {
                                     public String other() {
                                         return 'base'
                                     }
                                 }
                                 class Bar extends Foo {
                                     public String other() {
                                         return 'y'+super.other()
                                     }
                                 }
                                 class Baz extends Bar {
                                     public String other() {
                                         return 'z'+super.other()
                                     }
                                 }
                                 new Baz().other();
                                 """);
    }

    @Test
    @Issue("https://github.com/cloudbees/groovy-cps/issues/42")
    public void abstractMethod() throws Throwable {
        assertEvaluate(123, """
                            abstract class Foo {
                                abstract int val()
                            }
                            Foo foo = new Foo() {int val() {123}}
                            foo.val()
                            """);
    }

    @Issue("https://github.com/cloudbees/groovy-cps/issues/28")
    @Test
    @Ignore
    public void rehydrateClosure() throws Throwable {
        assertEvaluate("from Script instance", """
                                               class MyStrategy {
                                                   Closure<String> process() {
                                                       return {
                                                           speak()
                                                       }
                                                   }
                                               }
                                               String speak() {
                                                   'from Script instance'
                                               }
                                               Closure<String> closure = new MyStrategy().process()
                                               closure.rehydrate(this, this, this).call()
                                               """);
    }

    @Issue("https://github.com/cloudbees/groovy-cps/issues/16")
    @Test
    @Ignore("cannot easily be supported")
    public void category() throws Throwable {
        assertEvaluate("FOO", """
                              class BarCategory {
                                  static String up(String text) {
                                      text.toUpperCase()
                                  }
                              }
                              return use(BarCategory) {
                                  'foo'.up()
                              };
                              """);
    }

    @Issue("JENKINS-38268")
    @Test
    public void lexicalScope() throws Throwable {
        assertEvaluate(List.of(1, 1), """
                                      def a = [id: 'a', count: 0]
                                      def b = [id: 'b', count: 0]
                                      def toRun = [a, b].collect { thing -> return { thing.count = thing.count + 1 } }
                                      toRun.each { arg -> arg() }
                                      return [a.count, b.count]
                                      """);
    }

    @Issue("SECURITY-567")
    @Test
    public void methodPointer() throws Throwable {
        assertEvaluate("baseClass", """
                                    def b = new CpsTransformerTest.Base()
                                    return (b.&toString)() + (String.getClass().&getSimpleName)()
                                    """);
    }

    @Issue("JENKINS-32213")
    @Test
    public void allClassesSerializable() throws Throwable {
        evalCPSonly("class C {}; def c = new C(); assert c instanceof Serializable");
        evalCPSonly("class C implements Serializable {}; def c = new C(); assert c instanceof Serializable");
        assertTrue((boolean) evalCPSonly("""
                                         @NonCPS
                                         def anonymousClass() {
                                           def r = new Runnable() {
                                             @Override
                                             public void run() {}
                                           }
                                           return r instanceof Serializable
                                         }
                                         return anonymousClass()
                                         """));
    }

    @Issue("JENKINS-44027")
    @Test
    public void multipleAssignment() throws Throwable {
        assertEvaluate("firstsecondthirdfourth", """
                                                 def (a, b) = ['first', 'second']
                                                 def c, d
                                                 (c, d) = ['third', 'fourth']
                                                 return a + b + c + d
                                                 """);
    }

    @Issue("JENKINS-44027")
    @Test
    public void nonArrayLikeMultipleAssignment() throws Throwable {
        assertEvaluate(true, """
                             try {
                               def (a,b) = 4
                               return false
                             } catch (Exception e) {
                               return e instanceof MissingMethodException
                             }
                             """);
    }

    @Issue("JENKINS-44027")
    @Test
    public void arrayLikeMultipleAssignment() throws Throwable {
        assertEvaluate("wh", """
                             def (a,b) = 'what'
                             return a + b
                             """);
    }

    @Issue("JENKINS-44027")
    @Test
    public void mismatchedSizeMultipleAssignment() throws Throwable {
        assertEvaluate("first second fourth null", """
                                                   def (a, b) = ['first', 'second', 'third']
                                                   def (c, d) = ['fourth']
                                                   return [a, b, c, d].join(' ')
                                                   """);
    }

    @Issue("JENKINS-47363")
    @Test
    public void excessiveListElements() throws Throwable {
        String s1 = IntStream.range(0, 250).boxed().map(Object::toString).collect(Collectors.joining(",\n"));
        assertEvaluate(250, "def b = [" + s1 + "]\n" + "return b.size()\n");

        String s2 = IntStream.range(0, 251).boxed().map(Object::toString).collect(Collectors.joining(",\n"));
        evalCps("def b = [" + s2 + "]\n" + "return b.size()\n", ShouldFail.class, t -> {
            assertThat(t, instanceOf(MultipleCompilationErrorsException.class));
            assertThat(t.getMessage(), containsString("List expressions can only contain up to 250 elements"));
        });
    }

    @Issue("JENKINS-47363")
    @Test
    public void excessiveMapElements() throws Throwable {
        String s1 = IntStream.range(0, 125).boxed().map(i -> i + ":" + i).collect(Collectors.joining(",\n"));
        assertEvaluate(125, "def b = [" + s1 + "]\n" + "return b.size()\n");
        String s2 = IntStream.range(0, 126).boxed().map(i -> i + ":" + i).collect(Collectors.joining(",\n"));
        evalCps("def b = [" + s2 + "]\n" + "return b.size()\n", ShouldFail.class, t -> {
            assertThat(t, instanceOf(MultipleCompilationErrorsException.class));
            assertThat(t.getMessage(), containsString("Map expressions can only contain up to 125 entries"));
        });
    }

    @Issue("JENKINS-49679")
    @Test
    public void multipleAssignmentRunsMethodOnce() throws Throwable {
        assertEvaluate("firstsecondthirdfourth", """
                                                 alreadyRun = false
                                                 def getAandB() {
                                                   if (!alreadyRun) {
                                                     alreadyRun = true
                                                     return ['first', 'second']
                                                   } else {
                                                     return ['bad', 'worse']
                                                   }
                                                 }
                                                 def (a, b) = getAandB()
                                                 def c, d
                                                 (c, d) = ['third', 'fourth']
                                                 return a + b + c + d
                                                 """);
    }

    @Test
    public void mapEntryInBadContext() throws Throwable {
        evalCps("return [[a: 'a'], [b: 'b'][c: 'c']]", ShouldFail.class, e -> {
            ec.checkThat(e, instanceOf(MultipleCompilationErrorsException.class));
            ec.checkThat(
                    e.getMessage(),
                    containsString("Unsupported map entry expression for CPS transformation in this context"));
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
    @Test
    public void spreadExpression() throws Throwable {
        String[] declarations = new String[] {
            "def", // ArrayList
            "Object[]", // Object array
            "int[]" // Primitive array
        };
        for (String decl : declarations) {
            assertEvaluate(List.of(1, 2, 3, 4, 5), decl + " x = [1, 2, 3]\n" + "return [*x, 4, 5]\n");
            assertEvaluate(List.of(4, 1, 2, 3, 5), decl + " x = [1, 2, 3]\n" + "return [4, *x, 5]\n");
            assertEvaluate(List.of(4, 5, 1, 2, 3), decl + " x = [1, 2, 3]\n" + "return [4, 5, *x]\n");
            assertEvaluate(List.of(1, 2, 3), decl + " x = [1, 2, 3]\n" + "return [*x]\n");
            assertEvaluate(
                    List.of(1, 2, 3, 4, 5, 6, 7),
                    decl + " x = [2, 3]\n" + decl + " y = [5, 6]\n" + "return [1, *x, 4, *y, 7]\n");
        }
        assertEvaluate(Collections.singletonList(null), """
                                                        def x = null
                                                        return [*x]
                                                        """);
        assertFailsWithSameException("""
                                     def x = 1
                                     // *x cannot exist outside of list literals and method call arguments.
                                     return *x
                                     """);
    }

    @Issue("JENKINS-46163")
    @Test
    public void spreadMethodCallArguments() throws Throwable {
        String[] declarations = new String[] {
            "def", // ArrayList
            "Object[]", // Object array
            "int[]" // Primitive array
        };
        for (String decl : declarations) {
            assertEvaluate(
                    List.of(1, 2, 3),
                    decl + " x = [1, 2, 3]\n" + "def id(a, b, c) { [a, b, c] }\n" + "return id(*x)\n");
            assertEvaluate(
                    List.of(1, 2, 3),
                    decl + " x = [2, 3]\n" + "def id(a, b, c) { [a, b, c] }\n" + "return id(1, *x)\n");
            assertEvaluate(
                    List.of(1, 2, 3),
                    decl + " x = [1, 2]\n" + "def id(a, b, c) { [a, b, c] }\n" + "return id(*x, 3)\n");
            assertEvaluate(
                    List.of(1, 2, 3),
                    decl + " x = [2]\n" + "def id(a, b, c) { [a, b, c] }\n" + "return id(1, *x, 3)\n");
        }
        assertEvaluate(Arrays.asList(1, null, 3), """
                                                  def x = null
                                                  def id(a, b, c) { [a, b, c] }
                                                  return id(1, *x, 3)
                                                  """);
    }

    @Issue("JENKINS-46163")
    @Test
    public void spreadMapExpression() throws Throwable {
        assertEvaluate(InvokerHelper.createMap(new Object[] {"a", 1, "b", 2, "c", 3, "d", 4, "e", 5}), """
                                                                                                       def x = [a: 1, b: 2, c: 3]
                                                                                                       return [*:x, d: 4, e: 5]
                                                                                                       """);
        assertEvaluate(InvokerHelper.createMap(new Object[] {"d", 4, "a", 1, "b", 2, "c", 3, "e", 5}), """
                                                                                                       def x = [a: 1, b: 2, c: 3]
                                                                                                       return [d: 4, *:x, e: 5]
                                                                                                       """);
        assertEvaluate(InvokerHelper.createMap(new Object[] {"d", 4, "e", 5, "a", 1, "b", 2, "c", 3, "e", 5}), """
                                                                                                               def x = [a: 1, b: 2, c: 3]
                                                                                                               return [d: 4, e: 5, *:x]
                                                                                                               """);
        assertEvaluate(InvokerHelper.createMap(new Object[] {"a", 1, "b", 2, "c", -1}), """
                                                                                        def x = [a: 1, b: 2, c: 3]
                                                                                        // The final value for a key takes precedence.
                                                                                        return [c: 4, *:x, c: -1]
                                                                                        """);
        assertEvaluate(InvokerHelper.createMap(new Object[] {"a", 1, "b", 2, "c", 3}), """
                                                                                       def x = [a: 1, b: 2, c: 3]
                                                                                       return [*:x]
                                                                                       """);
        assertEvaluate(
                InvokerHelper.createMap(new Object[] {"a", 1, "b", 2, "c", 3, "d", 4, "e", 5, "f", 6, "g", 7}), """
                                                                                                                       def x = [b: 2, c: 3]
                                                                                                                       def y = [e: 5, f: 6]
                                                                                                                       return [a: 1, *:x, d: 4, *:y, g: 7]
                                                                                                                       """);
        // When used in method call arguments, *:map is the same as map, except for creating an instance of SpreadMap.
        // IDK why Groovy even allows the spread syntax here.
        assertEvaluate(Map.of("a", 1), """
                                       def x = [a: 1]
                                       def id(def m) { m }
                                       return id(*:x)
                                       """);
        assertFailsWithSameException("""
                                     def x = [a: 1]
                                     def id(String a, int i) { [a, i] }
                                     return id(*:x)
                                     """);
        assertFailsWithSameException("""
                                     def x = [a: 1]
                                     return *:x // *:x is syntactically invalid outside of map literals and method call arguments.
                                     """);
    }

    @Test
    public void initialExpressionsInMethodsAreCpsTransformed() throws Throwable {
        assertEvaluate(Boolean.FALSE, """
                                      def m1() { true }
                                      def m2(p = m1()){ false }
                                      m2()
                                      """);
    }

    @Test
    public void methodsWithInitialExpressionsAreExpandedToCorrectOverloads() throws Throwable {
        assertEvaluate(List.of("abc", "xbc", "xyc", "xyz"), """
                                                            def m2(a = 'a', b = 'b', c = 'c') {
                                                                a + b + c
                                                            }
                                                            def r1 = m2()
                                                            def r2 = m2('x')
                                                            def r3 = m2('x', 'y')
                                                            def r4 = m2('x', 'y', 'z')
                                                            [r1, r2, r3, r4]""");
        assertEvaluate(List.of("abc", "xbc", "xby"), """
                                                     def m2(a = 'a', b, c = 'c') {
                                                         a + b + c
                                                     }
                                                     def r1 = m2('b')
                                                     def r2 = m2('x', 'b')
                                                     def r3 = m2('x', 'b', 'y')
                                                     [r1, r2, r3]""");
    }

    @Test
    public void voidMethodsWithInitialExpressionsAreExpandedToCorrectOverloads() throws Throwable {
        assertEvaluate(List.of("abc", "xbc", "xyc", "xyz"), """
                                                            import groovy.transform.Field
                                                            @Field def r = []
                                                            void m2(a = 'a', b = 'b', c = 'c') {
                                                                r.add(a + b + c)
                                                            }
                                                            m2()
                                                            m2('x')
                                                            m2('x', 'y')
                                                            m2('x', 'y', 'z')
                                                            r""");
        assertEvaluate(List.of("abc", "xbc", "xby"), """
                                                     import groovy.transform.Field
                                                     @Field def r = []
                                                     void m2(a = 'a', b, c = 'c') {
                                                         r.add(a + b + c)
                                                     }
                                                     m2('b')
                                                     m2('x', 'b')
                                                     m2('x', 'b', 'y')
                                                     r""");
    }

    @Issue("JENKINS-57253")
    @Test
    public void illegalBreakStatement() throws Throwable {
        getBinding().setProperty("sentinel", 1);
        evalCps("sentinel = 2; break;", ShouldFail.class, e -> {
            assertThat(e.toString(), containsString("the break statement is only allowed inside loops or switches"));
        });
        assertEquals("Script should fail during compilation", 1, getBinding().getProperty("sentinel"));
    }

    @Ignore("groovy-cps does not cast method return values to the declared type")
    @Test
    public void methodReturnValuesShouldBeCastToDeclaredReturnType() throws Throwable {
        assertEvaluate(true, """
                             Boolean castToBoolean(def o) { o }
                             castToBoolean(123)
                             """);
    }

    @Test
    public void castToTypeShouldBeUsedForImplicitCasts() throws Throwable {
        assertEvaluate(List.of("toString", "toString", "toString", "asType"), """
                                                                              class Test {
                                                                                def auditLog = []
                                                                                @NonCPS
                                                                                def asType(Class c) {
                                                                                  auditLog.add('asType')
                                                                                  'Test.asType'
                                                                                }
                                                                                @NonCPS
                                                                                String toString() {
                                                                                  auditLog.add('toString')
                                                                                  'Test.toString'
                                                                                }
                                                                              }
                                                                              Test t = new Test()
                                                                              String variable = t
                                                                              String[] array = [t]
                                                                              (String)t
                                                                              t as String
                                                                              // This is the only cast that should call asType.
                                                                              t.auditLog
                                                                              """);
    }

    @Test
    public void castRelatedMethodsShouldBeNonCps() throws Throwable {
        // asType CPS (supported (to the extent possible) for compatibility with existing code)
        assertEvaluate(List.of(false, "asType class java.lang.Boolean"), """
                                                                         class Test {
                                                                           def auditLog = []
                                                                           def asType(Class c) {
                                                                             auditLog.add('asType ' + c)
                                                                             false
                                                                           }
                                                                         }
                                                                         def t = new Test()
                                                                         [t as Boolean, t.auditLog[0]]""");
        // asType NonCPS (preferred)
        assertEvaluate(List.of("asType class java.lang.Boolean"), """
                                                                  class Test {
                                                                    def auditLog = []
                                                                    @NonCPS
                                                                    def asType(Class c) {
                                                                      auditLog.add('asType ' + c)
                                                                      null
                                                                    }
                                                                  }
                                                                  def t = new Test()
                                                                  t as Boolean
                                                                  t.auditLog""");
        // asBoolean CPS (has never worked, still does not work)
        evalCps("""
                class Test {
                  def auditLog = []
                  def asBoolean() {
                    auditLog.add('asBoolean')
                  }
                }
                def t = new Test()
                (Boolean)t
                t.auditLog""", ShouldFail.class, t -> {
            ec.checkThat(
                    t.toString(),
                    equalTo(
                            "java.lang.IllegalStateException: Test.asBoolean must be @NonCPS; see: https://jenkins.io/redirect/pipeline-cps-method-mismatches/"));
        });
        // asBoolean NonCPS (required)
        assertEvaluate(List.of("asBoolean"), """
                                             class Test {
                                               def auditLog = []
                                               @NonCPS
                                               def asBoolean() {
                                                 auditLog.add('asBoolean')
                                               }
                                             }
                                             def t = new Test()
                                             (Boolean)t
                                             t.auditLog""");
    }

    @Test
    public void enums() throws Throwable {
        assertEvaluate("FIRST", "enum EnumTest { FIRST, SECOND }; EnumTest.FIRST.toString()");
        assertEvaluate("FIRST", "enum EnumTest { FIRST(), SECOND(); EnumTest() { } }; EnumTest.FIRST.toString()");
    }

    @Test
    public void anonymousClass() throws Throwable {
        assertEvaluate(6, """
                          def o = new Object() { def plusOne(x) { x + 1 } }
                          o.plusOne(5)""");
    }

    @Issue("JENKINS-62064")
    @Test
    public void assignmentExprsEvalToRHS() throws Throwable {
        assertEvaluate(List.of(1, 1, 1), """
                                         def a = b = c = 1
                                         [a, b, c]
                                         """);
        assertEvaluate(List.of(2, 3, 4), """
                                         def a = b = c = 1
                                         c += b += a += 1
                                         [a, b, c]
                                         """);
    }
}
