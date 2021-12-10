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
}
