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
        Object v = evalCPSonly(script)
        assert v==sh.evaluate(script); // make sure that regular non-CPS execution reports the same result
        return v;
    }

    Object evalCPSonly(String script) {
        Script s = csh.parse(script)
        Function f = s.run();
        def p = f.invoke(null, s, [], Continuation.HALT)

        def v = p.resume().yieldedValue()
        v
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

    @Test
    void variableAssignment() {
        assert evalCPS("x=3; x+=2; return x;")==5;
    }

    @Test
    void localVariable() {
        assert evalCPS("int x=3; x+=2; return x;")==5;
    }

    @Test
    void increment() {
        assert evalCPS("""
            x=0;
            y = x++;
            z = ++x;
            return x+"."+y+"."+z;
        """)=="2.0.2";
    }

    @Test
    void decrement() {
        assert evalCPS("""
            x=5;
            y = x--;
            z = --x;
            return x+"."+y+"."+z;
        """)=="3.5.3";
    }

    @Test
    void break_() {
        assert evalCPS("""
            x=0;
            int i=0;
            for (i=0; i<5; i+=1) {
                break;
                x+=1;
            }
            return i+x;
        """)==0;
    }

    @Test
    void globalBreak_() {
        assert evalCPS("""
            x=0;
            int i=0;
            int j=0;

            I:
            for (i=0; i<5; i+=1) {
                J:
                for (j=0; j<5; j+=1) {
                  break I;
                  x+=1;
                }
                x+=1;
            }
            return i+"."+j+"."+x;
        """)=="0.0.0";
    }
}
