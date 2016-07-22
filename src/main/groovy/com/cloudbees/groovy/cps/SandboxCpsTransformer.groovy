package com.cloudbees.groovy.cps

import com.cloudbees.groovy.cps.sandbox.Untrusted
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer
import org.codehaus.groovy.ast.ClassNode
import org.codehaus.groovy.ast.MethodNode
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.SourceUnit
import org.kohsuke.groovy.sandbox.SandboxTransformer

/**
 * {@link CpsTransformer} + {@link org.kohsuke.groovy.sandbox.SandboxTransformer}
 *
 * @author Kohsuke Kawaguchi
 */
class SandboxCpsTransformer extends CpsTransformer {
    private final SandboxTransformer st = new SandboxTransformer()
    private ClassCodeExpressionTransformer stv;

    @Override
    void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        stv = st.createVisitor(source)
        super.call(source, context, classNode)
    }

    /**
     * If the method is not CPS transformed, we need to sandbox-transform that method to intercept calls
     * that happen in these methods.
     */
    @Override
    protected void visitNontransformedMethod(MethodNode m) {
        stv.visitMethod(m);
    }

    @Override
    protected Class getTrustTag() {
        return Untrusted.class;
    }
}
