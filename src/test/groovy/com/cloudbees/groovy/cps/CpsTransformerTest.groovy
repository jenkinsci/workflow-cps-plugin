package com.cloudbees.groovy.cps

import org.codehaus.groovy.control.CompilerConfiguration
import org.codehaus.groovy.control.customizers.ImportCustomizer
import org.junit.Before
import org.junit.Test

import java.awt.Point

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
        binding.foo = "FOO"
        binding.bar = "BAR"
        binding.zot = 5
        binding.point = new Point(1,2)
        binding.points = [new Point(1,2),new Point(3,4)]
        binding.intArray = [0,1,2,3,4] as int[]

        def cc = new CompilerConfiguration()
        cc.addCompilationCustomizers(new ImportCustomizer().addImports(CpsTransformerTest.class.name))
        cc.addCompilationCustomizers(new CpsTransformer())
        sh = new GroovyShell(binding,cc);
    }

    @Test
    void helloWorld() {
        sh.evaluate("'hello world'.length()")
    }
}
