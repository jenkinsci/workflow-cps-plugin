package com.cloudbees.groovy.cps

import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.classgen.BytecodeExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.syntax.Types

import static org.codehaus.groovy.syntax.Types.*

/**
 *
 *
 * @author Kohsuke Kawaguchi
 */
class CpsTransformer extends CompilationCustomizer implements GroovyCodeVisitor {
    CpsTransformer() {
        super(CompilePhase.CANONICALIZATION)
    }

    @Override
    void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        def ast = source.getAST();

        ast.methods?.each { visitMethod(it) }
        classNode?.declaredConstructors?.each { visitMethod(it) }
        classNode?.methods?.each { visitMethod(it) }
//        classNode?.objectInitializerStatements?.each { it.visit(visitor) }
//        classNode?.fields?.each { visitor.visitField(it) }
    }

    /**
     * Should this method be transformed?
     */
    private boolean shouldBeTransformed(MethodNode node) {
        if (node.name=="run" && node.returnType.name==Object.class.name && extendsFromScript(node.declaringClass))
            return true;    // default body of the script
        return node.annotations.find { it.classNode.name==WorkflowMethod.class.name } != null;
    }

    private boolean extendsFromScript(ClassNode c) {
        while (c!=null) {
            if (c.name==Script.class.name)
                return true;
            c = c.superClass
        }
        return false;
    }

    /**
     * Transforms asynchronous workflow method.
     *
     * From:
     *
     * ReturnT foo( T1 arg1, T2 arg2, ...) {
     *    ... body ...
     * }
     *
     * To:
     *
     * Function foo( T1 arg1, T2 arg2, ...) {
     *   return new Function(['arg1','arg2','arg3',...], CPS-transformed-method-body)
     * }
     */
    public void visitMethod(MethodNode m) {
        if (!shouldBeTransformed(m))
            return;

        // function shall now return the Function object
        m.returnType = FUNCTION_TYPE;

        def body;

        // transform the body
        parent = { e -> body=e }
        m.code.visit(this)

        def params = new ListExpression();
        m.parameters.each { params.addExpression(new ConstantExpression(it.name))}

        m.code = new ReturnStatement(new ConstructorCallExpression(FUNCTION_TYPE, new TupleExpression(params,body)));
    }

    /**
     * As we visit expressions in the method body, we convert them to the {@link Builder} invocations
     * and pass them back to this closure.
     */
    private Closure parent;

    private void visit(ASTNode e) {
        e.visit(this);
    }

    private void visit(Collection<? extends ASTNode> col) {
        for (def e : col) {
            e.visit(this);
        }
    }

    /**
     * Makes an AST fragment that calls {@link Builder} with specific method.
     *
     * @param methodName
     *      Method on {@link Builder} to call.
     * @param args
     *      Can be closure for building argument nodes, Expression, or List of Expressions.
     */
    private void makeNode(String methodName, Object args) {
        if (args instanceof Closure) {
            def argExps = []
            def old = parent;
            try {
                parent = { a -> argExps.add(a) }

                args(); // evaluate arguments
                args = argExps;
            } finally {
                parent = old
            }
        }

        def tuple = args==null ? new TupleExpression() : new TupleExpression(args)
        parent(new MethodCallExpression(BUILDER, methodName, tuple));
    }

    private void makeNode(String methodName) {
        makeNode(methodName,null)
    }

    /**
     * Used in the closure block of {@link #makeNode(String, Object)} to create a literal string argument.
     */
    private void literal(String s) {
        parent(new ConstantExpression(s))
    }

    private void literal(ClassNode c) {
        parent(new ClassExpression(c))
    }

    void visitMethodCallExpression(MethodCallExpression call) {
        makeNode("functionCall") {
            visit(call.objectExpression);
            // TODO: spread & safe
            visit(call.method);
            visit(((TupleExpression)call.arguments).expressions)
        }
    }

    void visitBlockStatement(BlockStatement b) {
        makeNode("block") {
            visit(b.statements)
        }
    }

    void visitForLoop(ForStatement forLoop) {
        if (forLoop.variable==ForStatement.FOR_LOOP_DUMMY) {
            // for ( e1; e2; e3 ) { ... }
            ClosureListExpression loop = forLoop.collectionExpression
            assert loop.expressions.size()==3;

            makeNode("forLoop") {
                literal(forLoop.statementLabel)
                visit(loop.expressions)
                visit(forLoop.loopBlock)
            }
        } else {
            // for (x in col) { ... }
            makeNode("forInLoop") {
                literal(forLoop.statementLabel)
                literal(forLoop.variableType)
                literal(forLoop.variable.name)
                visit(forLoop.collectionExpression)
                visit(forLoop.loopBlock)
            }
        }
    }

    void visitWhileLoop(WhileStatement loop) {
        throw new UnsupportedOperationException();
    }

    void visitDoWhileLoop(DoWhileStatement loop) {
        throw new UnsupportedOperationException();
    }

    void visitIfElse(IfStatement ifElse) {
        throw new UnsupportedOperationException();
    }

    void visitExpressionStatement(ExpressionStatement statement) {
        visit(statement.expression)
    }

    void visitReturnStatement(ReturnStatement statement) {
        makeNode("return_") {
            visit(statement.expression);
        }
    }

    void visitAssertStatement(AssertStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitTryCatchFinally(TryCatchStatement finally1) {
        throw new UnsupportedOperationException();
    }

    void visitSwitch(SwitchStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitCaseStatement(CaseStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitBreakStatement(BreakStatement statement) {
        makeNode("break_", new ConstantExpression(statement.label));
    }

    void visitContinueStatement(ContinueStatement statement) {
        makeNode("continue_", new ConstantExpression(statement.label));
    }

    void visitThrowStatement(ThrowStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitSynchronizedStatement(SynchronizedStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitCatchStatement(CatchStatement statement) {
        throw new UnsupportedOperationException();
    }

    void visitStaticMethodCallExpression(StaticMethodCallExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitConstructorCallExpression(ConstructorCallExpression call) {
        makeNode("new_") {
            literal(call.type)
            visit(((TupleExpression)call.arguments).expressions)
        }
    }

    void visitTernaryExpression(TernaryExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitShortTernaryExpression(ElvisOperatorExpression expression) {
        throw new UnsupportedOperationException();
    }

    private static Map<Integer,String> BINARY_OP_TO_BUILDER_METHOD = [
            (COMPARE_EQUAL)                 :"compareEqual",
            (COMPARE_NOT_EQUAL)             :"compareNotEqual",
            (COMPARE_TO)                    :"compareTo",
            (COMPARE_GREATER_THAN)          :"greaterThan",
            (COMPARE_GREATER_THAN_EQUAL)    :"greaterThanEqual",
            (COMPARE_LESS_THAN)             :"lessThan",
            (COMPARE_LESS_THAN_EQUAL)       :"lessThanEqual",
            (LOGICAL_AND)                   :"logicanAnd",
            (LOGICAL_OR)                    :"logicanOr",
            (BITWISE_AND)                   :"bitwiseAnd",
            (BITWISE_OR)                    :"bitwiseOr",
            (BITWISE_XOR)                   :"bitwiseXor",
            (PLUS)                          :"plus",
            (PLUS_EQUAL)                    :"plusEqual",
            (MINUS)                         :"minus",
            (MULTIPLY)                      :"multiply",
            (DIVIDE)                        :"div",
            (INTDIV)                        :"intdiv",
            (MOD)                           :"mod",
            (POWER)                         :"power",
            (EQUAL)                         :"assign",
    ]

    /**
     * @see org.codehaus.groovy.classgen.asm.BinaryExpressionHelper#eval(BinaryExpression)
     */
    void visitBinaryExpression(BinaryExpression exp) {
        def body = {// for building CPS tree for two expressions
            visit(exp.leftExpression)
            visit(exp.rightExpression)
        }

        def name = BINARY_OP_TO_BUILDER_METHOD[exp.operation.type]
        if (name!=null) {
            makeNode(name,body)
            return;
        }

/* TODO: from BinaryExpressionHelper
        // other unique cases
        switch (exp.operation.type) {

        case BITWISE_AND_EQUAL:
            evaluateBinaryExpressionWithAssignment("and", exp);
            break;

        case BITWISE_OR_EQUAL:
            evaluateBinaryExpressionWithAssignment("or", exp);
            break;

        case BITWISE_XOR_EQUAL:
            evaluateBinaryExpressionWithAssignment("xor", exp);
            break;

        case MINUS_EQUAL:
            evaluateBinaryExpressionWithAssignment("minus", exp);
            break;

        case MULTIPLY_EQUAL:
            evaluateBinaryExpressionWithAssignment("multiply", exp);
            break;

        case DIVIDE_EQUAL:
            //SPG don't use divide since BigInteger implements directly
            //and we want to dispatch through DefaultGroovyMethods to get a BigDecimal result
            evaluateBinaryExpressionWithAssignment("div", exp);
            break;

        case INTDIV_EQUAL:
            evaluateBinaryExpressionWithAssignment("intdiv", exp);
            break;

        case MOD_EQUAL:
            evaluateBinaryExpressionWithAssignment("mod", exp);
            break;

        case POWER_EQUAL:
            evaluateBinaryExpressionWithAssignment("power", exp);
            break;

        case LEFT_SHIFT:
            evaluateBinaryExpression("leftShift", exp);
            break;

        case LEFT_SHIFT_EQUAL:
            evaluateBinaryExpressionWithAssignment("leftShift", exp);
            break;

        case RIGHT_SHIFT:
            evaluateBinaryExpression("rightShift", exp);
            break;

        case RIGHT_SHIFT_EQUAL:
            evaluateBinaryExpressionWithAssignment("rightShift", exp);
            break;

        case RIGHT_SHIFT_UNSIGNED:
            evaluateBinaryExpression("rightShiftUnsigned", exp);
            break;

        case RIGHT_SHIFT_UNSIGNED_EQUAL:
            evaluateBinaryExpressionWithAssignment("rightShiftUnsigned", exp);
            break;

        case KEYWORD_INSTANCEOF:
            evaluateInstanceof(exp);
            break;

        case FIND_REGEX:
            evaluateCompareExpression(findRegexMethod, exp);
            break;

        case MATCH_REGEX:
            evaluateCompareExpression(matchRegexMethod, exp);
            break;

        case LEFT_SQUARE_BRACKET:
            if (controller.getCompileStack().isLHS()) {
                evaluateEqual(exp, false);
            } else {
                evaluateBinaryExpression("getAt", exp);
            }
            break;

        case KEYWORD_IN:
            evaluateCompareExpression(isCaseMethod, exp);
            break;
*/

        throw new UnsupportedOperationException("Operation: " + exp.operation + " not supported");
    }

    void visitPrefixExpression(PrefixExpression exp) {
        makeNode("prefix"+ prepostfixOperatorSuffix(exp)) {
            visit(exp.expression)
        }
    }

    void visitPostfixExpression(PostfixExpression exp) {
        makeNode("postfix"+ prepostfixOperatorSuffix(exp)) {
            visit(exp.expression)
        }
    }

    private String prepostfixOperatorSuffix(Expression exp) {
        switch (exp.operation.type) {
        case PLUS_PLUS:   return "Inc";
        case MINUS_MINUS: return "Dec";
        default:
            throw new UnsupportedOperationException("Unknown operator:" + exp.operation.text)
        }
    }

    void visitBooleanExpression(BooleanExpression expression) {
        visit(expression);
    }

    void visitClosureExpression(ClosureExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitTupleExpression(TupleExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitMapExpression(MapExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitMapEntryExpression(MapEntryExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitListExpression(ListExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitRangeExpression(RangeExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitPropertyExpression(PropertyExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitAttributeExpression(AttributeExpression attributeExpression) {
        throw new UnsupportedOperationException();
    }

    void visitFieldExpression(FieldExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitMethodPointerExpression(MethodPointerExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitConstantExpression(ConstantExpression expression) {
        makeNode("constant", expression)
    }

    void visitClassExpression(ClassExpression expression) {
        makeNode("constant", expression)
    }

    void visitVariableExpression(VariableExpression exp) {
        def ref = exp.accessedVariable
        if (ref instanceof VariableExpression /* local variable */
        ||  ref instanceof Parameter) {
            makeNode("localVariable") {
                literal(exp.name)
            }
        } else
        if (ref instanceof DynamicVariable
        ||  ref instanceof PropertyNode
        ||  ref instanceof FieldNode) {
            makeNode("property") {
                makeNode("this_")
                literal(exp.name)
            }
        } else
        if (exp.name=="this") {
            /* Kohsuke: TODO: I don't really understand the 'true' block of the code, so I'm missing something
                if (controller.isStaticMethod() || (!controller.getCompileStack().isImplicitThis() && controller.isStaticContext())) {
                    if (controller.isInClosure()) classNode = controller.getOutermostClass();
                    visitClassExpression(new ClassExpression(classNode));
                } else {
                    loadThis();
                }
             */
            makeNode("this_")
        } else
            throw new UnsupportedOperationException("Unexpected variable type: ${ref}");
    }

    void visitDeclarationExpression(DeclarationExpression exp) {
        if (exp.isMultipleAssignmentDeclaration()) {
            // def (a,b)=list
            makeNode("sequence") {
                for (VariableExpression v in exp.tupleExpression.expressions) {
                    makeNode("declareVariable") {
                        literal(v.type)
                        literal(v.name)
                    }
                }
                makeNode("assign") {
                    visit(exp.leftExpression)
                    visit(exp.rightExpression)
                }
            }
        } else {
            // def x=v;
            makeNode("declareVariable") {
                def v = exp.variableExpression
                literal(v.type)
                literal(v.name)
                visit(exp.rightExpression) // this will not produce anything if this is EmptyExpression
            }
        }
    }

    void visitGStringExpression(GStringExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitArrayExpression(ArrayExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitSpreadExpression(SpreadExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitSpreadMapExpression(SpreadMapExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitNotExpression(NotExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitUnaryMinusExpression(UnaryMinusExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitUnaryPlusExpression(UnaryPlusExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitBitwiseNegationExpression(BitwiseNegationExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitCastExpression(CastExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitArgumentlistExpression(ArgumentListExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitClosureListExpression(ClosureListExpression closureListExpression) {
        throw new UnsupportedOperationException();
    }

    void visitBytecodeExpression(BytecodeExpression expression) {
        throw new UnsupportedOperationException();
    }

    private static final ClassNode FUNCTION_TYPE = ClassHelper.makeCached(Function.class);
    private static final ClassNode BUILDER_TYPE = ClassHelper.makeCached(Builder.class);
    private static final PropertyExpression BUILDER = new PropertyExpression(new ClassExpression(BUILDER_TYPE), "INSTANCE")
}
