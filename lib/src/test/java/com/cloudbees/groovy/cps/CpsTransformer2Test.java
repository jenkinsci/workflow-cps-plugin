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

package com.cloudbees.groovy.cps;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

public class CpsTransformer2Test extends AbstractGroovyCpsTest {

    @Test
    public void initialExpressionsInMethodsAreCpsTransformed() throws Throwable {
        assertEquals(Boolean.FALSE, evalCPS(
                "def m1() { true }\n" +
                "def m2(p = m1()){ false }\n" +
                "m2()\n"));
    }

    @Test public void methodsWithInitialExpressionsAreExpandedToCorrectOverloads() throws Throwable {
        assertEquals(Arrays.asList("abc", "xbc", "xyc", "xyz"), evalCPS(
                "def m2(a = 'a', b = 'b', c = 'c') {\n" +
                "    a + b + c\n" +
                "}\n" +
                "def r1 = m2()\n" +
                "def r2 = m2('x')\n" +
                "def r3 = m2('x', 'y')\n" +
                "def r4 = m2('x', 'y', 'z')\n" +
                "[r1, r2, r3, r4]"));
        assertEquals(Arrays.asList("abc", "xbc", "xby"), evalCPS(
                "def m2(a = 'a', b, c = 'c') {\n" +
                "    a + b + c\n" +
                "}\n" +
                "def r1 = m2('b')\n" +
                "def r2 = m2('x', 'b')\n" +
                "def r3 = m2('x', 'b', 'y')\n" +
                "[r1, r2, r3]"));
    }

    @Test public void voidMethodsWithInitialExpressionsAreExpandedToCorrectOverloads() throws Throwable {
        assertEquals(Arrays.asList("abc", "xbc", "xyc", "xyz"), evalCPS(
                "import groovy.transform.Field\n" +
                "@Field def r = []\n" +
                "void m2(a = 'a', b = 'b', c = 'c') {\n" +
                "    r.add(a + b + c)\n" +
                "}\n" +
                "m2()\n" +
                "m2('x')\n" +
                "m2('x', 'y')\n" +
                "m2('x', 'y', 'z')\n" +
                "r"));
        assertEquals(Arrays.asList("abc", "xbc", "xby"), evalCPS(
                "import groovy.transform.Field\n" +
                "@Field def r = []\n" +
                "void m2(a = 'a', b, c = 'c') {\n" +
                "    r.add(a + b + c)\n" +
                "}\n" +
                "m2('b')\n" +
                "m2('x', 'b')\n" +
                "m2('x', 'b', 'y')\n" +
                "r"));
    }

    @Issue("JENKINS-57253")
    @Test public void illegalBreakStatement() {
        getBinding().setProperty("sentinel", 1);
        try {
            evalCPSonly("sentinel = 2; break;");
            fail("Execution should fail");
        } catch (Exception e) {
            assertThat(e.toString(), containsString("the break statement is only allowed inside loops or switches"));
        }
        assertEquals("Script should fail during compilation", 1, getBinding().getProperty("sentinel"));
    }

    @Ignore("groovy-cps does not cast method return values to the declared type")
    @Test public void methodReturnValuesShouldBeCastToDeclaredReturnType() throws Throwable {
        assertEquals(true, evalCPS(
                "Boolean castToBoolean(def o) { o }\n" +
                "castToBoolean(123)\n"));
    }

    @Test public void castToTypeShouldBeUsedForImplicitCasts() throws Throwable {
        assertEquals(Arrays.asList("toString", "toString", "toString", "asType"), evalCPS(
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
                "t.auditLog\n"));
    }

    @Test public void castRelatedMethodsShouldBeNonCps() throws Throwable {
        // asType CPS (supported (to the extent possible) for compatibility with existing code)
        assertEquals(Arrays.asList(false, "asType class java.lang.Boolean"), evalCPS(
                "class Test {\n" +
                "  def auditLog = []\n" +
                "  def asType(Class c) {\n" +
                "    auditLog.add('asType ' + c)\n" +
                "    false\n" +
                "  }\n" +
                "}\n" +
                "def t = new Test()\n" +
                "[t as Boolean, t.auditLog[0]]"));
        // asType NonCPS (preferred)
        assertEquals(Collections.singletonList("asType class java.lang.Boolean"), evalCPS(
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
                "t.auditLog"));
        // asBoolean CPS (has never worked, still does not work)
        try {
            evalCPS(
                "class Test {\n" +
                "  def auditLog = []\n" +
                "  def asBoolean() {\n" +
                "    auditLog.add('asBoolean')\n" +
                "  }\n" +
                "}\n" +
                "def t = new Test()\n" +
                "(Boolean)t\n" +
                "t.auditLog");
            fail("Should have thrown an exception");
        } catch (Throwable t) {
            assertEquals("java.lang.IllegalStateException: Test.asBoolean must be @NonCPS; see: https://jenkins.io/redirect/pipeline-cps-method-mismatches/", t.toString());
        }
        // asBoolean NonCPS (required)
        assertEquals(Collections.singletonList("asBoolean"), evalCPS(
                "class Test {\n" +
                "  def auditLog = []\n" +
                "  @NonCPS\n" +
                "  def asBoolean() {\n" +
                "    auditLog.add('asBoolean')\n" +
                "  }\n" +
                "}\n" +
                "def t = new Test()\n" +
                "(Boolean)t\n" +
                "t.auditLog"));
    }

    @Test
    public void enums() throws Throwable {
        assertEquals("FIRST", evalCPS(
                "enum EnumTest { FIRST, SECOND }; EnumTest.FIRST.toString()"));
        assertEquals("FIRST", evalCPS(
                "enum EnumTest { FIRST(), SECOND(); EnumTest() { } }; EnumTest.FIRST.toString()"));
    }

    @Test
    public void anonymousClass() throws Throwable {
        assertEquals(6, evalCPS(
                "def o = new Object() { def plusOne(x) { x + 1 } }\n" +
                "o.plusOne(5)"));
    }
}
