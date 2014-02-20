package com.cloudbees.groovy.cps

import com.cloudbees.groovy.cps.impl.CpsFunction
import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.junit.Before

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
abstract class AbstractGroovyCpsTest {
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
        def imports = new ImportCustomizer()
                .addImports(CpsTransformerTest.class.name)
                .addImports(WorkflowMethod.class.name)

        def cc = new CompilerConfiguration()
        cc.addCompilationCustomizers(imports)
        cc.addCompilationCustomizers(new CpsTransformer())
        cc.scriptBaseClass = SerializableScript.class.name
        csh = new GroovyShell(binding,cc);

        cc = new CompilerConfiguration()
        cc.addCompilationCustomizers(imports)
        sh = new GroovyShell(binding,cc);
    }

    Object evalCPS(String script) {
        Object v = evalCPSonly(script)
        assert v==sh.evaluate(script); // make sure that regular non-CPS execution reports the same result
        return v;
    }

    Object evalCPSonly(String script) {
        Script s = csh.parse(script)
        CpsFunction f = s.run();
        def p = f.invoke(null, s, [], Continuation.HALT)

        def v = p.resume().yieldedValue()
        v
    }

}
