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
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.jvnet.hudson.test.Issue;
import org.kohsuke.groovy.sandbox.ClassRecorder;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class SandboxInvoker2Test extends AbstractGroovyCpsTest {
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

        cr.register();
        try {
            return parseCps(script).invoke(e, null, Continuation.HALT).run().yield.replay();
        } finally {
            cr.unregister();
        }
    }

    public void assertIntercept(String... expected) {
        assertThat(cr.toString().split("\n"), equalTo(expected));
    }

    @Issue("SECURITY-1710")
    @Test public void methodParametersWithInitialExpressions() throws Throwable {
        evalCpsSandbox("def m(p = System.getProperties()){ true }; m()");
        assertIntercept(
                "Script1.super(Script1).setBinding(Binding)",
                "Script1.m()",
                "System:getProperties()",
                "Checker:checkedCast(Class,Properties,Boolean,Boolean,Boolean)",
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
}
