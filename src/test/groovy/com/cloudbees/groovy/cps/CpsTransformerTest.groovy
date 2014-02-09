package com.cloudbees.groovy.cps

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.junit.Before
import org.junit.Test

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class CpsTransformerTest {
    GroovyShell sh;
    def binding = new Binding()

    @Before
    void setUp() {
        def cc = new CompilerConfiguration()
        cc.addCompilationCustomizers(new ImportCustomizer().addImports(CpsTransformerTest.class.name))
        cc.addCompilationCustomizers(new CpsTransformer())
        sh = new GroovyShell(binding,cc);
    }

    @Test
    void helloWorld() {
        Function f = sh.evaluate("'hello world'.length()")
        def p = f.invoke(null, null, [], Continuation.HALT)

        assert p.resume().yieldedValue()==11;
    }
}
