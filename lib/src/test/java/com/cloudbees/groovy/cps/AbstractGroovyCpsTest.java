package com.cloudbees.groovy.cps;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.fail;

import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.concurrent.atomic.AtomicReference;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.customizers.ImportCustomizer;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.ErrorCollector;
import org.kohsuke.groovy.sandbox.impl.GroovyCallSiteSelector;

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class AbstractGroovyCpsTest {
    @Rule
    public ErrorCollector ec = new ErrorCollector();

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
        imports.addStarImports(
                "com.cloudbees.groovy.cps", getClass().getPackage().getName());

        CompilerConfiguration cc = new CompilerConfiguration();
        cc.addCompilationCustomizers(imports);
        cc.addCompilationCustomizers(createCpsTransformer());
        cc.setScriptBaseClass(SerializableScript.class.getName());
        csh = new GroovyShell(binding, cc);

        cc = new CompilerConfiguration();
        cc.addCompilationCustomizers(imports);
        sh = new GroovyShell(binding, cc);
    }

    /**
     * @return a GroovyShell that has {@link CpsTransformer} enabled and will CPS-transform all scripts
     */
    public GroovyShell getCsh() {
        return csh;
    }

    /**
     * @return a regular GroovyShell that will not CPS-transform scripts
     */
    public GroovyShell getSh() {
        return sh;
    }

    public Binding getBinding() {
        return binding;
    }

    protected CpsTransformer createCpsTransformer() {
        return new CpsTransformer();
    }

    /**
     * Use {@code ShouldFail.class} as the expected result for {@link #eval} and similar methods when the expression is expected to throw an exception.
     */
    public static final class ShouldFail {}

    @FunctionalInterface
    public interface ExceptionHandler {
        public void handleException(Throwable e) throws Exception;
    }

    protected void eval(String script, Object expectedResult, ExceptionHandler handler) {
        try {
            Object actual = sh.evaluate(script);
            String actualType = GroovyCallSiteSelector.getName(actual);
            String expectedType = GroovyCallSiteSelector.getName(expectedResult);
            ec.checkThat(
                    "Non-CPS-transformed result (" + actualType + ") does not match expected result (" + expectedType
                            + ")",
                    actual,
                    equalTo(expectedResult));
        } catch (Throwable t) {
            ec.checkSucceeds(() -> {
                handler.handleException(t);
                return null;
            });
        }
    }

    protected void evalCps(String script, Object expectedResult, ExceptionHandler handler) {
        try {
            Object actual = parseCps(script)
                    .invoke(null, null, Continuation.HALT)
                    .run(10000)
                    .replay();
            String actualType = GroovyCallSiteSelector.getName(actual);
            String expectedType = GroovyCallSiteSelector.getName(expectedResult);
            ec.checkThat(
                    "Non-CPS-transformed result (" + actualType + ") does not match expected result (" + expectedType
                            + ")",
                    actual,
                    equalTo(expectedResult));
        } catch (Throwable t) {
            ec.checkSucceeds(() -> {
                handler.handleException(t);
                return null;
            });
        }
    }

    /**
     * Execute a Groovy expression both with and without the CPS transformation and check that the return value matches
     * the expected value in both cases.
     * @param expectedReturnValue The expected return value for running the script.
     * @param script The Groovy script to execute.
     */
    public void assertEvaluate(Object expectedReturnValue, String script) {
        evalCps(script, expectedReturnValue, e -> {
            throw new RuntimeException("Failed to evaluate CPS-transformed script: " + script, e);
        });
        eval(script, expectedReturnValue, e -> {
            throw new RuntimeException("Failed to evaluate non-CPS-transformed script: " + script, e);
        });
    }

    /**
     * Execute a Groovy expression both with and without the CPS transformation and check that the script throws an
     * exception with the same class and message in both cases.
     * @param expression The Groovy expression to execute.
     */
    public void assertFailsWithSameException(String expression) {
        AtomicReference<Throwable> cpsException = new AtomicReference<>();
        evalCps(expression, ShouldFail.class, cpsException::set);
        AtomicReference<Throwable> nonCpsException = new AtomicReference<>();
        eval(expression, ShouldFail.class, nonCpsException::set);
        if (cpsException.get() == null || nonCpsException.get() == null) {
            return; // Either evalCps or eval will have already recorded an error because the result was not ShouldFail.
        }
        ec.checkThat(
                "CPS-transformed and non-CPS-transformed exceptions should have the same type",
                nonCpsException.get().getClass(),
                equalTo(cpsException.get().getClass()));
        ec.checkThat(
                "CPS-transformed and non-CPS-transformed exceptions should have the same message",
                nonCpsException.get().getMessage(),
                equalTo(cpsException.get().getMessage()));
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

        ObjectInputStreamWithLoader ois =
                new ObjectInputStreamWithLoader(new ByteArrayInputStream(baos.toByteArray()), csh.getClassLoader());

        return (T) ois.readObject();
    }
}
