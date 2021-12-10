package com.cloudbees.groovy.cps

import com.cloudbees.groovy.cps.green.GreenThread
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.junit.Assert
import org.junit.Before

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
abstract class AbstractGroovyCpsTest extends Assert {
    /**
     * CPS-transforming shelll
     */
    GroovyShell csh;

    /**
     * Default groovy shell
     */
    GroovyShell sh;

    Binding binding = new Binding()

    @Before
    void setUp() {
        def imports = new ImportCustomizer()
            .addStarImports([CpsTransformerTest.class, GreenThread.class, getClass()]*.package*.name as String[])

        def cc = new CompilerConfiguration()
        cc.addCompilationCustomizers(imports)
        cc.addCompilationCustomizers(createCpsTransformer())
        cc.scriptBaseClass = SerializableScript.class.name
        csh = new GroovyShell(binding,cc);

        cc = new CompilerConfiguration()
        cc.addCompilationCustomizers(imports)
        sh = new GroovyShell(binding,cc);
    }

    protected CpsTransformer createCpsTransformer() {
        return new CpsTransformer()
    }

    Object evalCPS(String script) {
        Object resultInCps = evalCPSonly(script)
        assert resultInCps==sh.evaluate(script); // make sure that regular non-CPS execution reports the same result
        return resultInCps;
    }

    Object evalCPSonly(String script) {
        return parseCps(script).invoke(null, null, Continuation.HALT).run(10000).replay()
    }

    CpsCallableInvocation parseCps(String script) {
        Script s = csh.parse(script)
        try {
            s.run();
            fail "Expecting CPS transformation"
        } catch (CpsCallableInvocation inv) {
            return inv;
        }
    }

    public <T> T roundtripSerialization(T cx) {
        def baos = new ByteArrayOutputStream()

        new ObjectOutputStream(baos).writeObject(cx);

        def ois = new ObjectInputStreamWithLoader(
                new ByteArrayInputStream(baos.toByteArray()),
                csh.classLoader)

        return ois.readObject()
    }
}
