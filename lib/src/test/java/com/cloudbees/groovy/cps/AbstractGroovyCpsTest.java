package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.junit.Before;
import org.kohsuke.groovy.sandbox.impl.GroovyCallSiteSelector;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractGroovyCpsTest {
    /**
     * CPS-transforming shelll
     */
    private GroovyShell csh;

    /**
     * Default groovy shell
     */
    private GroovyShell sh;

    private Binding binding = new Binding();

    @Before
    public void setUp() {
        ImportCustomizer imports = new ImportCustomizer();
        imports.addStarImports("com.cloudbees.groovy.cps", getClass().getPackage().getName());

        CompilerConfiguration cc = new CompilerConfiguration();
        cc.addCompilationCustomizers(imports);
        cc.addCompilationCustomizers(createCpsTransformer());
        cc.setScriptBaseClass(SerializableScript.class.getName());
        csh = new GroovyShell(binding,cc);

        cc = new CompilerConfiguration();
        cc.addCompilationCustomizers(imports);
        sh = new GroovyShell(binding,cc);
    }
    
    public GroovyShell getCsh() {
        return csh;
    }
    
    public Binding getBinding() {
        return binding;
    }

    protected CpsTransformer createCpsTransformer() {
        return new CpsTransformer();
    }

    public void assertEvaluate(Object expectedResult, String script) throws Throwable {
        Object actualCps = evalCPSonly(script);
        String actualCpsType = GroovyCallSiteSelector.getName(actualCps);
        String expectedType = GroovyCallSiteSelector.getName(expectedResult);
        assertThat("CPS-transformed result (" + actualCpsType + ") does not match expected result (" + expectedType + ")", actualCps, equalTo(expectedResult));
        Object actualNonCps = sh.evaluate(script);
        String actualNonCpsType = GroovyCallSiteSelector.getName(actualNonCps);
        assertThat("Non-CPS-transformed result (" + actualNonCpsType + ") does not match expected result (" + expectedType + ")", actualNonCps, equalTo(expectedResult));
    }

    public Object evalCPS(String script) throws Throwable {
        Object resultInCps = evalCPSonly(script);
        assertThat(resultInCps, equalTo(sh.evaluate(script))); // make sure that regular non-CPS execution reports the same result
        return resultInCps;
    }

    public Object evalCPSonly(String script) throws Throwable {
        return parseCps(script).invoke(null, null, Continuation.HALT).run(10000).replay();
    }

    public CpsCallableInvocation parseCps(String script) {
        Script s = csh.parse(script);
        try {
            s.run();
            fail("Expecting CPS transformation");
        } catch (CpsCallableInvocation inv) {
            return inv;
        }
        throw new AssertionError("Expecting CpsCallableInvocation");
    }

    public <T> T roundtripSerialization(T cx) throws ClassNotFoundException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        new ObjectOutputStream(baos).writeObject(cx);

        ObjectInputStreamWithLoader ois = new ObjectInputStreamWithLoader(
                new ByteArrayInputStream(baos.toByteArray()),
                csh.getClassLoader());

        return (T)ois.readObject();
    }
}
