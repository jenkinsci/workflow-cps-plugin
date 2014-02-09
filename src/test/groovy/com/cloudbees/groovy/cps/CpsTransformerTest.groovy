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
    /**
     * CPS-transforming shelll
     */
    GroovyShell csh;

    /**
     * Default groovy shell
     */
    GroovyShell sh;

    def binding = new Binding()

    @Before
    void setUp() {
        def imports = new ImportCustomizer().addImports(CpsTransformerTest.class.name)

        def cc = new CompilerConfiguration()
        cc.addCompilationCustomizers(imports)
        cc.addCompilationCustomizers(new CpsTransformer())
        csh = new GroovyShell(binding,cc);

        cc = new CompilerConfiguration()
        cc.addCompilationCustomizers(imports)
        sh = new GroovyShell(binding,cc);
    }

    @Test
    void helloWorld() {
        Script s = csh.parse("'hello world'.length()")
        Function f = s.run();
        def p = f.invoke(null, s, [], Continuation.HALT)

        assert p.resume().yieldedValue()==11;

        assert sh.evaluate("'hello world'.length()")==11;
    }
}
