package com.cloudbees.groovy.cps

import com.cloudbees.groovy.cps.impl.CpsCallableInvocation
import com.cloudbees.groovy.cps.impl.CpsFunction
import org.codehaus.groovy.ast.*
import org.codehaus.groovy.ast.expr.*
import org.codehaus.groovy.ast.stmt.*
import org.codehaus.groovy.classgen.BytecodeExpression
import org.codehaus.groovy.classgen.GeneratorContext
import org.codehaus.groovy.classgen.Verifier
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer

import java.lang.annotation.Annotation
import java.lang.reflect.Modifier

import static org.codehaus.groovy.syntax.Types.*

/**
 * Performs CPS transformation of Groovy methods.
 *
 * <p>
 * Every method annotated with {@link WorkflowMethod} gets rewritten. The general strategy of CPS transformation is
 * as follows:
 *
 * <p>
 * Before:
 * <pre>
 * Object foo(int x, int y) {
 *   return x+y;
 * }
 * </pre>
 *
 * <p>
 * After:
 * <pre>
 * CpsFunction foo(int x, int y) {
 *   return foo$workflow;
 * }
 * static CpsFunction foo$workflow = new CpsFunction(["x","y"], B.plus(B.localVariable("x"), B.localVariable("y"));
 * </pre>
 * ("B" refers to {@link Builder#INSTANCE} for brevity)
 *
 * <p>
 * That is, we transform a Groovy AST of the method body into a tree of {@link Block}s by using {@link Builder#INSTANCE},
 * then the method just returns this function object and expect the caller to evaluate it, instead of executing the method
 * synchronously before it returns.
 *
 * <p>
 * This class achieves this transformation by implementing {@link GroovyCodeVisitor} and traverse Groovy AST tree
 * in the in-order. As we traverse this tree, we produce another Groovy AST tree that invokes {@link Builder}.
 * Note that we aren't calling Builder directly here; that's supposed to happen when the Groovy code under transformation
 * actually runs.
 *
 * <p>
 * Groovy AST that calls {@link Builder} is a tree of function call, so we build {@link MethodCallExpression}s
 * in the top-down manner. We do this by {@link #makeNode(String)}, which creates a call to {@code Builder.xxx(...)},
 * then supply the closure that fills in the arguments to this call by walking down the original Groovy AST tree.
 * This walk-down is done by calling {@link #visit(ASTNode)} (to recursively visit ASTs), or by calling {@link #literal(Object)}
 * methods, which generate string/class/etc literals, as sometimes {@link Builder} methods need them as well.
 *
 * @author Kohsuke Kawaguchi
 */
class CpsTransformer extends CompilationCustomizer implements GroovyCodeVisitor {
    private int iota=0;

    CpsTransformer() {
        super(CompilePhase.CANONICALIZATION)
    }

    @Override
    void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        source.ast.methods?.each { visitMethod(it) }
//        classNode?.declaredConstructors?.each { visitMethod(it) } // can't transform constructor
        classNode?.methods?.each { visitMethod(it) }
//        classNode?.objectInitializerStatements?.each { it.visit(visitor) }
//        classNode?.fields?.each { visitor.visitField(it) }


        // groovy puts timestamp of compilation into a class file, causing serialVersionUID to change.
        // this tends to be undesirable for CPS involving persistence.
        // set the timestamp to some bogus value will prevent Verifier from adding a field that encodes
        // timestamp in the field name
        // see http://stackoverflow.com/questions/15310136/neverhappen-variable-in-compiled-classes
        if (classNode.getField(Verifier.__TIMESTAMP)==null)
            classNode.addField(Verifier.__TIMESTAMP,Modifier.STATIC|Modifier.PRIVATE, ClassHelper.long_TYPE,
            new ConstantExpression(0L));
    }

    /**
     * Should this method be transformed?
     */
    private boolean shouldBeTransformed(MethodNode node) {
        if (node.name=="run" && node.returnType.name==Object.class.name && extendsFromScript(node.declaringClass))
            return true;    // default body of the script
        return hasAnnotation(node, WorkflowMethod.class) && !hasAnnotation(node, WorkflowTransformed.class);
    }

    private boolean hasAnnotation(MethodNode node, Class<? extends Annotation> a) {
        node.annotations.find { it.classNode.name == a.name } != null
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
     * private static CpsFunction ___cps___N = new CpsFunction(['arg1','arg2','arg3',...], CPS-transformed-method-body)
     * ReturnT foo( T1 arg1, T2 arg2, ...) {
     *   throw new CpsCallableInvocation(___cps___N, this, arg1, arg2, ...)
     * }
     */
    public void visitMethod(MethodNode m) {
        if (!shouldBeTransformed(m))
            return;

        def body;

        // transform the body
        parent = { e -> body=e }
        m.code.visit(this)

        def params = new ListExpression();
        m.parameters.each { params.addExpression(new ConstantExpression(it.name))}

        def f = m.declaringClass.addField("___cps___${iota++}", Modifier.STATIC|Modifier.STATIC, FUNCTION_TYPE,
                new ConstructorCallExpression(FUNCTION_TYPE, new TupleExpression(params, body)));

        def args = new TupleExpression(new VariableExpression(f), THIS);
        m.parameters.each { args.addExpression(new VariableExpression(it)) }

        m.code = new ThrowStatement(new ConstructorCallExpression(CPSCALLINVK_TYPE,args));

        m.addAnnotation(new AnnotationNode(WORKFLOW_TRANSFORMED_TYPE));
    }

    /**
     * As we visit expressions in the method body, we convert them to the {@link Builder} invocations
     * and pass them back to this closure.
     */
    private Closure parent;

    private void visit(ASTNode e) {
        if (e instanceof EmptyExpression) {
            // working around a bug in EmptyExpression.visit() that doesn't call any method
            visitEmptyExpression(e);
        } else
        if (e instanceof EmptyStatement) {
            // working around a bug in EmptyStatement.visit() that doesn't call any method
            visitEmptyStatement(e);
        } else {
            e.visit(this);
        }
    }

    private void visit(Collection<? extends ASTNode> col) {
        for (def e : col) {
            visit(e);
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
        parent(new MethodCallExpression(BUILDER, methodName, makeChildren(args)));
    }

    /**
     * Makes an AST fragment that instantiates a new instance of the  given type.
     *
     * @param args
     *      Can be closure for building argument nodes, Expression, or List of Expressions.
     */
    private void makeNode(ClassNode type, Object args) {
        parent(new ConstructorCallExpression(type, makeChildren(args)));
    }

    /**
     * Given closure, {@link Expression} or a list of them, package them up into
     * {@link TupleExpression}
     */
    private TupleExpression makeChildren(args) {
        if (args==null)     return new TupleExpression();
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

        return new TupleExpression(args);
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

    void visitEmptyExpression(EmptyExpression e) {
        makeNode("noop")
    }

    void visitEmptyStatement(EmptyStatement e) {
        makeNode("noop")
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
        makeNode("while_") {
            literal(loop.statementLabel)
            visit(loop.booleanExpression)
            visit(loop.loopBlock)
        }
    }

    void visitDoWhileLoop(DoWhileStatement loop) {
        makeNode("doWhile") {
            literal(loop.statementLabel)
            visit(loop.booleanExpression)
            visit(loop.loopBlock)
        }
    }

    void visitIfElse(IfStatement stmt) {
        makeNode("if_") {
            visit(stmt.booleanExpression)
            visit(stmt.ifBlock)
            visit(stmt.elseBlock)
        }
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

    void visitTryCatchFinally(TryCatchStatement stmt) {
        // TODO: finally block
        makeNode("tryCatch") {
            visit(stmt.tryStatement)
            visit(stmt.catchStatements)
        }
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

    void visitCatchStatement(CatchStatement stmt) {
        makeNode(CATCH_EXPRESSION_TYPE) {
            literal(stmt.exceptionType)
            literal(stmt.variable.name)
            visit(stmt.code)
        }
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

    void visitBooleanExpression(BooleanExpression exp) {
        visit(exp.expression);
    }

    void visitClosureExpression(ClosureExpression exp) {
        makeNode("closure") {
            def params = new ListExpression();

            // the interpretation of the 'parameters' is messed up. According to ClosureWriter,
            // when the user explicitly defines no parameter "{ -> foo() }" then this is null,
            // when the user doesn't define any parameter explicitly { foo() }, then this is empty,
            if (exp.parameters==null) {
            } else
            if (exp.parameters.length==0) {
                params.addExpression(new ConstantExpression("it"));
            } else {
                exp.parameters.each { params.addExpression(new ConstantExpression(it.name)) }
            }
            parent(params)
            visit(exp.code)
        }
    }

    void visitTupleExpression(TupleExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitMapExpression(MapExpression exp) {
        makeNode("map") {
            exp.mapEntryExpressions.each { e ->
                visit(e.keyExpression)
                visit(e.valueExpression)
            }
        }
    }

    void visitMapEntryExpression(MapEntryExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitListExpression(ListExpression exp) {
        makeNode("list") {
            visit(exp.expressions)
        }
    }

    void visitRangeExpression(RangeExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitPropertyExpression(PropertyExpression exp) {
        // TODO: spread and safe
        makeNode("property") {
            visit(exp.objectExpression)
            visit(exp.property)
        }
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

    private static final ClassNode FUNCTION_TYPE = ClassHelper.makeCached(CpsFunction.class);
    private static final ClassNode CATCH_EXPRESSION_TYPE = ClassHelper.makeCached(CatchExpression.class);
    private static final ClassNode BUILDER_TYPE = ClassHelper.makeCached(Builder.class);
    private static final ClassNode CPSCALLINVK_TYPE = ClassHelper.makeCached(CpsCallableInvocation.class);
    private static final ClassNode WORKFLOW_TRANSFORMED_TYPE = ClassHelper.makeCached(WorkflowTransformed.class);
    private static final PropertyExpression BUILDER = new PropertyExpression(new ClassExpression(BUILDER_TYPE), "INSTANCE")
    private static final VariableExpression THIS = new VariableExpression("this");

    /**
     * Closure's default "it" parameter.
     */
    private static final Parameter IT = new Parameter(ClassHelper.OBJECT_TYPE, "it", ConstantExpression.NULL);
}
