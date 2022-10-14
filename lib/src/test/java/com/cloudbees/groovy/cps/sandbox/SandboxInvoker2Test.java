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
import java.io.File;
import static org.hamcrest.CoreMatchers.equalTo;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.groovy.sandbox.ClassRecorder;

public class SandboxInvoker2Test extends AbstractGroovyCpsTest {
    @Rule
    public ErrorCollector ec = new ErrorCollector();

    ClassRecorder cr = new ClassRecorder();

    @Override
    protected CpsTransformer createCpsTransformer() {
        return new SandboxCpsTransformer();
    }

    @Before public void zeroIota() {
        CpsTransformer.iota.set(0);
    }

    private Object evalCpsSandbox(String script) throws Throwable {
        FunctionCallEnv e = (FunctionCallEnv)Envs.empty();
        e.setInvoker(new SandboxInvoker());

        cr.reset();
        cr.register();
        try {
            return parseCps(script).invoke(e, null, Continuation.HALT).run().yield.replay();
        } finally {
            cr.unregister();
        }
    }

    public void assertIntercept(String... expected) {
        ec.checkThat(cr.toString().split("\n"), equalTo(expected));
    }

    public void assertIntercept(String script, Object expectedValue, String... expected) throws Throwable {
        ec.checkThat(evalCpsSandbox(script), equalTo(expectedValue));
        assertIntercept(expected);
    }

    @Issue("SECURITY-1710")
    @Test public void methodParametersWithInitialExpressions() throws Throwable {
        evalCpsSandbox("def m(p = System.getProperties()){ true }; m()");
        assertIntercept(
                "Script1.super(Script1).setBinding(Binding)",
                "Script1.m()",
                "System:getProperties()",
                "Script1.m(Properties)");
    }

    @Test public void constructorParametersWithInitialExpressions() throws Throwable {
        evalCpsSandbox(
                "class Test {\n" +
                "  Test(p = System.getProperties()) { }" +
                "}\n" +
                "new Test()");
        assertIntercept(
                "Script1.super(Script1).setBinding(Binding)",
                "new Test()",
                "System:getProperties()");
    }

    @Ignore("Initial expressions for parameters in CPS-transformed closures are currently ignored")
    @Test public void closureParametersWithInitialExpressions() throws Throwable {
        // Fails because p is null in the body of the closure.
        assertEquals(true, evalCpsSandbox("{ p = System.getProperties() -> p != null }()"));
        assertIntercept(
                "Script1.super(Script1).setBinding(Binding)",
                "CpsClosure.call()",
                "System:getProperties()", // Not currently intercepted because it is dropped by the transformer.
                "ScriptBytecodeAdapter:compareNotEqual(null,null)");
    }

    @Issue("SECURITY-2824")
    @Test public void sandboxInterceptsImplicitCastsVariableAssignment() throws Throwable {
        assertEquals(new File("secret.key"), evalCpsSandbox(
                "File file\n" + // DeclarationExpression
                "file = ['secret.key']\n " + // BinaryExpression
                "file"));
        assertIntercept(
                "Script1.super(Script1).setBinding(Binding)",
                "new File(String)");
    }

    @Issue("SECURITY-2824")
    @Test
    public void sandboxInterceptsImplicitCastsArrayAssignment() throws Throwable {
        // Regular Groovy casts the rhs of array assignments to match the component type of the array, but the
        // sandbox does not do this (with or without the CPS transformation). Ideally the sandbox would have the same
        // behavior as regular Groovy, but the current behavior is safe, which is good enough.
        try {
            evalCpsSandbox(
                "File[] files = [null]\n" +
                "files[0] = ['secret.key']\n " +
                "files[0]");
            fail("The sandbox must intercept unsafe array element assignments");
        } catch (Throwable t) {
            assertEquals("java.lang.ArrayStoreException: java.util.ArrayList", t.toString());
        }
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

}
