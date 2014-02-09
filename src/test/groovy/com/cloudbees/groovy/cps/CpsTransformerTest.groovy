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

    Object evalCPS(String script) {
        Script s = csh.parse(script)
        Function f = s.run();
        def p = f.invoke(null, s, [], Continuation.HALT)

        def v = p.resume().yieldedValue()

        assert v==sh.evaluate(script);
        return v;
    }

    @Test
    void helloWorld() {
        assert evalCPS("'hello world'.length()")==11
    }

    @Test
    void comparison() {
        for(int i in [1,2,3]) {
            for (int j in [1,2,3]) {
                assert evalCPS("${i} < ${j}") == (i<j);
                assert evalCPS("${i} <= ${j}")== (i<=j);
                assert evalCPS("${i} > ${j}") == (i>j);
                assert evalCPS("${i} >= ${j}")== (i>=j);
            }
        }
    }

    @Test
    void forInLoop() {
        assert evalCPS("x=0; for (i in [1,2,3,4,5]) x+=i; return x;")==15;
    }
}
