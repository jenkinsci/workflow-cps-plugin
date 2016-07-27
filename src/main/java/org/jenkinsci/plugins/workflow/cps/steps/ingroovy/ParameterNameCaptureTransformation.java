package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.expr.ConstantExpression;
import org.codehaus.groovy.ast.expr.ListExpression;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.kohsuke.stapler.CaptureParameterNameTransformation;
import org.kohsuke.stapler.CapturedParameterNames;

import java.util.List;

/**
 * Capture every method parameter name.
 *
 * <p>
 * Unlike {@link CaptureParameterNameTransformation}, this transformer captures parameter names for everything.
 *
 * @author Kohsuke Kawaguchi
 * @see CaptureParameterNameTransformation
 */
@GroovyASTTransformation
public class ParameterNameCaptureTransformation implements ASTTransformation {
    public void visit(ASTNode[] nodes, SourceUnit source) {
        handleClasses(source.getAST().getClasses());
    }

    private void handleClasses(List<ClassNode> classNodes) {
        for (ClassNode c : classNodes) {
            for (MethodNode m : c.getMethods()) {
                write(m);
            }
        }
    }

    /**
     * Captures the parameter names as annotations on the class.
     */
    private void write(MethodNode c) {
        ListExpression v = new ListExpression();
        for (org.codehaus.groovy.ast.Parameter p : c.getParameters())
            v.addExpression(new ConstantExpression(p.getName()));

        AnnotationNode a = new AnnotationNode(new ClassNode(CapturedParameterNames.class));
        a.addMember("value", v);
        c.addAnnotation(a);
    }
}
