package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.sandbox.Untrusted;
import org.codehaus.groovy.ast.ClassCodeExpressionTransformer;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.CastExpression;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.DeclarationExpression;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.ast.expr.TupleExpression;
import org.codehaus.groovy.ast.expr.VariableExpression;
import org.codehaus.groovy.ast.stmt.Statement;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.control.SourceUnit;
import org.kohsuke.groovy.sandbox.SandboxTransformer;

import java.util.List;

/**
 * {@link CpsTransformer} + {@link SandboxTransformer}
 *
 * @author Kohsuke Kawaguchi
 */
public class SandboxCpsTransformer extends CpsTransformer {
    private final SandboxTransformer st = new SandboxTransformer();

    private ClassCodeExpressionTransformer stv;

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        stv = st.createVisitor(source, classNode);
        super.call(source, context, classNode);
    }

    @Override
    protected void processConstructors(ClassNode classNode) {
        st.processConstructors(stv, classNode);
    }

    /**
     * If the method is not CPS transformed, we need to sandbox-transform that
     * method to intercept calls that happen in these methods.
     * This includes all constructor bodies.
     * @see SandboxTransformer#call
     */
    @Override
    protected void visitNontransformedMethod(MethodNode m) {
        stv.visitMethod(m);
    }

    /**
     * Field initializers are never transformed, but we still need to run the sandbox transformer on them.
     * @see SandboxTransformer#call
     */
    @Override
    protected void visitNontransformedField(FieldNode f) {
        stv.visitField(f);
    }

    /**
     * Miscellaneous statements like object initializers are never transformed, but we still need to run the sandbox transformer on them.
     * @see SandboxTransformer#call
     */
    @Override
    protected void visitNontransformedStatement(Statement s) {
        s.visit(stv);
    }

    @Override
    public void visitCastExpression(final CastExpression exp) {
        if (exp.isCoerce()) {
            makeNode("sandboxCast", new Runnable() {
                @Override
                public void run() {
                    loc(exp);
                    visit(exp.getExpression());
                    literal(exp.getType());
                    literal(exp.isIgnoringAutoboxing());
                    literal(exp.isStrict());
                }
            });
        } else {
            super.visitCastExpression(exp);
        }
    }

    @Override
    protected void visitAssignmentOrCast(final VariableExpression varExp, final Expression rhs) {
        if (SandboxTransformer.mightBePositionalArgumentConstructor(varExp)) {
            makeNode("sandboxCast", new Runnable() {
                @Override
                public void run() {
                    loc(varExp);
                    visit(rhs);
                    literal(varExp.getType());
                    literal(false);
                    literal(false);
                }
            });
        } else {
            super.visitAssignmentOrCast(varExp, rhs);
        }
    }

    @Override
    protected void getMultipleAssignmentValueOrCast(final VariableExpression varExp, final Expression rhs, final Expression index) {
        if (SandboxTransformer.mightBePositionalArgumentConstructor(varExp)) {
            makeNode("sandboxCast", new Runnable() {
                @Override
                public void run() {
                    loc(varExp);
                    makeNode("array", new Runnable() {
                        @Override
                        public void run() {
                            loc(rhs);
                            visit(rhs);
                            makeNode("constant", index);
                        }
                    });
                    literal(varExp.getType());
                    literal(false);
                    literal(false);
                }
            });
        } else {
            super.getMultipleAssignmentValueOrCast(varExp, rhs, index);
        }
    }

    @Override
    protected Class getTrustTag() {
        return Untrusted.class;
    }
}
