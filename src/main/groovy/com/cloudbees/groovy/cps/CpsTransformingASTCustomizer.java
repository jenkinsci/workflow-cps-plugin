package com.cloudbees.groovy.cps;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;

/**
 * @author Kohsuke Kawaguchi
 */
@GroovyASTTransformation(phase=CompilePhase.CANONICALIZATION)
public class CpsTransformingASTCustomizer implements ASTTransformation {
    public void visit(ASTNode[] nodes, SourceUnit source) {
        new CpsTransformer().call(source, null, (ClassNode) nodes[1]);
    }
}
