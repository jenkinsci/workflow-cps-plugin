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
import org.codehaus.groovy.control.Janitor
import org.codehaus.groovy.control.SourceUnit
import org.codehaus.groovy.control.customizers.CompilationCustomizer
import org.codehaus.groovy.runtime.powerassert.SourceText
import org.codehaus.groovy.syntax.Token

import java.lang.annotation.Annotation
import java.lang.reflect.Modifier

import static org.codehaus.groovy.syntax.Types.*

/**
 * Performs CPS transformation of Groovy methods.
 *
 * <p>
 * Every method not annotated with {@link NonCPS} gets rewritten. The general strategy of CPS transformation is
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
 *
 * private static CpsFunction ___cps___N = ___cps___N();
 *
 * private static final CpsFunction ___cps___N() {
 *   Builder b = new Builder(...);
 *   return new CpsFunction(['x','y'], b.plus(b.localVariable("x"), b.localVariable("y"))
 * }
 * </pre>
 *
 * <p>
 * That is, we transform a Groovy AST of the method body into a tree of {@link Block}s by using {@link Builder},
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
 * in the top-down manner. We do this by {@link CpsTransformer#makeNode(String)}, which creates a call to {@code Builder.xxx(...)},
 * then supply the closure that fills in the arguments to this call by walking down the original Groovy AST tree.
 * This walk-down is done by calling {@link CpsTransformer#visit(ASTNode)} (to recursively visit ASTs), or by calling {@link CpsTransformer#literal(String)}
 * methods, which generate string/class/etc literals, as sometimes {@link Builder} methods need them as well.
 *
 * @author Kohsuke Kawaguchi
 */
class CpsTransformer extends CompilationCustomizer implements GroovyCodeVisitor {
    private int iota=0;
    private SourceUnit sourceUnit;

    CpsTransformer() {
        super(CompilePhase.CANONICALIZATION)
    }

    @Override
    void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        this.sourceUnit = source;
        copy(source.ast.methods)?.each { visitMethod(it) }
//        classNode?.declaredConstructors?.each { visitMethod(it) } // can't transform constructor
        copy(classNode?.methods)?.each { visitMethod(it) }
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

    private <T> List<T> copy(List<T> t) {
        if (t==null)    return t;
        else            return new ArrayList<T>(t);
    }

    /**
     * Should this method be transformed?
     */
    protected boolean shouldBeTransformed(MethodNode node) {
        return !node.isSynthetic() && !hasAnnotation(node, NonCPS.class) && !hasAnnotation(node, WorkflowTransformed.class);
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
     * private static CpsFunction ___cps___N = ___cps___N();
     *
     * private static final CpsFunction ___cps___N() {
     *   return new CpsFunction(['arg1','arg2','arg3',...], CPS-transformed-method-body)
     * }
     *
     * ReturnT foo( T1 arg1, T2 arg2, ...) {
     *   throw new CpsCallableInvocation(___cps___N, this, arg1, arg2, ...)
     * }
     */
    public void visitMethod(MethodNode m) {
        if (!shouldBeTransformed(m)) {
            visitNontransformedMethod(m);
            return;
        }

        Expression body;

        // transform the body
        parent = { e -> body=e }
        m.code.visit(this)

        def params = new ListExpression();
        m.parameters.each { params.addExpression(new ConstantExpression(it.name))}

        /*
              CpsFunction ___cps___N() {
                Builder b = new Builder(new MethodLocation(...));
                return new CpsFunction( << parameters >>, << body: AST tree building code >>);
              }
         */

        def cpsName = "___cps___${iota++}"

        def builderMethod = m.declaringClass.addMethod(cpsName, PRIVATE_STATIC_FINAL, FUNCTION_TYPE, new Parameter[0], new ClassNode[0],
            new BlockStatement([
                new ExpressionStatement(new DeclarationExpression(BUILDER, new Token(ASSIGN, "=", -1, -1),
                        new ConstructorCallExpression(BUIDER_TYPE, new TupleExpression(
                                        new ConstructorCallExpression(METHOD_LOCATION_TYPE, new TupleExpression(
                                            new ConstantExpression(m.declaringClass.name),
                                            new ConstantExpression(m.name),
                                            new ConstantExpression(sourceUnit.name)
                                        ))
                                    )))),
                new ReturnStatement(new ConstructorCallExpression(FUNCTION_TYPE, new TupleExpression(params, body)))
            ], new VariableScope())
        )
        builderMethod.addAnnotation(new AnnotationNode(WORKFLOW_TRANSFORMED_TYPE))

        def f = m.declaringClass.addField(cpsName, PRIVATE_STATIC_FINAL, FUNCTION_TYPE,
                new StaticMethodCallExpression(m.declaringClass, cpsName, new TupleExpression()));
//                new ConstructorCallExpression(FUNCTION_TYPE, new TupleExpression(params, body)));


        def args = new TupleExpression(new VariableExpression(f), THIS);
        m.parameters.each { args.addExpression(new VariableExpression(it)) }

        m.code = new ThrowStatement(new ConstructorCallExpression(CPSCALLINVK_TYPE,args));

        m.addAnnotation(new AnnotationNode(WORKFLOW_TRANSFORMED_TYPE));
    }

    /**
     * For methods that are not CPS-transformed.
     */
    protected void visitNontransformedMethod(MethodNode m) {
    }


    /**
     * As we visit expressions in the method body, we convert them to the {@link Builder} invocations
     * and pass them back to this closure.
     */
    private Closure parent;

    protected void visit(ASTNode e) {
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

    protected void visit(Collection<? extends ASTNode> col) {
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

    protected void loc(ASTNode e) {
        literal(e.lineNumber);
    }

    /**
     * Used in the closure block of {@link #makeNode(String, Object)} to create a literal string argument.
     */
    protected void literal(String s) {
        parent(new ConstantExpression(s))
    }

    protected void literal(ClassNode c) {
        parent(new ClassExpression(c))
    }

    protected void literal(int n) {
        parent(new ConstantExpression(n,true))
    }

    protected void literal(boolean b) {
        parent(new ConstantExpression(b,true))
    }

    void visitEmptyExpression(EmptyExpression e) {
        makeNode("noop")
    }

    void visitEmptyStatement(EmptyStatement e) {
        makeNode("noop")
    }

    void visitMethodCallExpression(MethodCallExpression call) {
        makeNode("functionCall") {
            loc(call)
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
                loc(forLoop);
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
        def j = new Janitor()
        def text = new SourceText(statement, sourceUnit, j).normalizedText
        j.cleanup()

        makeNode("assert_") {
            visit(statement.booleanExpression)
            visit(statement.messageExpression)
            literal(text)
        }
    }

    void visitTryCatchFinally(TryCatchStatement stmt) {
        makeNode("tryCatch") {
            visit(stmt.tryStatement)
            visit(stmt.finallyStatement)
            visit(stmt.catchStatements)
        }
    }

    void visitSwitch(SwitchStatement stmt) {
        makeNode("switch_") {
            literal(stmt.statementLabel)
            visit(stmt.expression)
            visit(stmt.defaultStatement)
            visit(stmt.caseStatements)
        }
    }

    void visitCaseStatement(CaseStatement stmt) {
        makeNode("case_") {
            loc(stmt)
            visit(stmt.expression)
            visit(stmt.code)
        }
    }

    void visitBreakStatement(BreakStatement statement) {
        makeNode("break_", new ConstantExpression(statement.label));
    }

    void visitContinueStatement(ContinueStatement statement) {
        makeNode("continue_", new ConstantExpression(statement.label));
    }

    void visitThrowStatement(ThrowStatement st) {
        makeNode("throw_") {
            visit(st.expression)
        }
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

    void visitStaticMethodCallExpression(StaticMethodCallExpression exp) {
        makeNode("staticCall") {
            loc(exp)
            literal(exp.ownerType)
            literal(exp.method)
            visit(((TupleExpression)exp.arguments).expressions)
        }
    }

    void visitConstructorCallExpression(ConstructorCallExpression call) {
        makeNode("new_") {
            loc(call)
            literal(call.type)
            visit(((TupleExpression)call.arguments).expressions)
        }
    }

    void visitTernaryExpression(TernaryExpression exp) {
        makeNode("ternaryOp") {
            visit(exp.booleanExpression)
            visit(exp.trueExpression)
            visit(exp.falseExpression)
        }
    }

    void visitShortTernaryExpression(ElvisOperatorExpression exp) {
        makeNode("elvisOp") {
            visit(exp.booleanExpression)
            visit(exp.falseExpression)
        }
    }

    // Constants from Token.type to a method on Builder
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
            (BITWISE_AND_EQUAL)             :"bitwiseAndEqual",
            (BITWISE_OR)                    :"bitwiseOr",
            (BITWISE_OR_EQUAL)              :"bitwiseOrEqual",
            (BITWISE_XOR)                   :"bitwiseXor",
            (BITWISE_XOR_EQUAL)             :"bitwiseXorEqual",
            (PLUS)                          :"plus",
            (PLUS_EQUAL)                    :"plusEqual",
            (MINUS)                         :"minus",
            (MINUS_EQUAL)                   :"minusEqual",
            (MULTIPLY)                      :"multiply",
            (MULTIPLY_EQUAL)                :"multiplyEqual",
            (DIVIDE)                        :"div",
            (DIVIDE_EQUAL)                  :"divEqual",
            (INTDIV)                        :"intdiv",
            (INTDIV_EQUAL)                  :"intdivEqual",
            (MOD)                           :"mod",
            (MOD_EQUAL)                     :"modEqual",
            (POWER)                         :"power",
            (POWER_EQUAL)                   :"powerEqual",
            (EQUAL)                         :"assign",
            (KEYWORD_INSTANCEOF)            :"instanceOf",
            (LEFT_SQUARE_BRACKET)           :"array",
    ]

    /**
     * @see org.codehaus.groovy.classgen.asm.BinaryExpressionHelper#eval(BinaryExpression)
     */
    void visitBinaryExpression(BinaryExpression exp) {
        def body = {// for building CPS tree for two expressions
            loc(exp)
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

        case FIND_REGEX:
            evaluateCompareExpression(findRegexMethod, exp);
            break;

        case MATCH_REGEX:
            evaluateCompareExpression(matchRegexMethod, exp);
            break;

        case KEYWORD_IN:
            evaluateCompareExpression(isCaseMethod, exp);
            break;
*/

        throw new UnsupportedOperationException("Operation: " + exp.operation + " not supported");
    }

    void visitPrefixExpression(PrefixExpression exp) {
        makeNode("prefix"+ prepostfixOperatorSuffix(exp)) {
            loc(exp)
            visit(exp.expression)
        }
    }

    void visitPostfixExpression(PostfixExpression exp) {
        makeNode("postfix"+ prepostfixOperatorSuffix(exp)) {
            loc(exp)
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

    void visitRangeExpression(RangeExpression exp) {
        makeNode("range") {
            loc(exp)
            visit(exp.from)
            visit(exp.to)
            literal(exp.inclusive)
        }
    }

    void visitPropertyExpression(PropertyExpression exp) {
        // TODO: spread and safe
        makeNode("property") {
            loc(exp)
            visit(exp.objectExpression)
            visit(exp.property)
        }
    }

    void visitAttributeExpression(AttributeExpression exp) {
        // TODO: spread and safe
        makeNode("attribute") {
            loc(exp)
            visit(exp.objectExpression)
            visit(exp.property)
        }
    }

    void visitFieldExpression(FieldExpression exp) {
        def f = exp.field
        if (f.isStatic()) {
            makeNode("staticField") {
                loc(exp)
                literal(f.type)
                literal(exp.fieldName)
            }
        } else {
            makeNode("property") {
                loc(exp)
                makeNode("this_")
                literal(exp.fieldName)
            }
        }
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
                loc(exp)
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
                        loc(exp)
                        literal(v.type)
                        literal(v.name)
                    }
                }
                makeNode("assign") {
                    loc(exp)
                    visit(exp.leftExpression)
                    visit(exp.rightExpression)
                }
            }
        } else {
            // def x=v;
            makeNode("declareVariable") {
                def v = exp.variableExpression
                loc(exp)
                literal(v.type)
                literal(v.name)
                visit(exp.rightExpression) // this will not produce anything if this is EmptyExpression
            }
        }
    }

    void visitGStringExpression(GStringExpression exp) {
        makeNode("gstring") {
            loc(exp)
            makeNode("list") {
                visit(exp.values)
            }
            makeNode("list") {
                visit(exp.strings)
            }
        }
    }

    void visitArrayExpression(ArrayExpression exp) {
        if (exp.sizeExpression!=null) {
            // array instanation like new String[1][2][3]
            makeNode("newArray") {
                loc(exp)
                literal(exp.elementType)
                visit(exp.sizeExpression)
            }
        } else {
            throw new UnsupportedOperationException();
        }
    }

    void visitSpreadExpression(SpreadExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitSpreadMapExpression(SpreadMapExpression expression) {
        throw new UnsupportedOperationException();
    }

    void visitNotExpression(NotExpression exp) {
        makeNode("not") {
            loc(exp)
            visit(exp.expression)
        }
    }

    void visitUnaryMinusExpression(UnaryMinusExpression exp) {
        makeNode("unaryMinus") {
            loc(exp)
            visit(exp.expression)
        }
    }

    void visitUnaryPlusExpression(UnaryPlusExpression exp) {
        makeNode("unaryPlus") {
            loc(exp)
            visit(exp.expression)
        }
    }

    void visitBitwiseNegationExpression(BitwiseNegationExpression exp) {
        makeNode("bitwiseNegation") {
            loc(exp)
            visit(exp.expression)
        }
    }

    void visitCastExpression(CastExpression exp) {
        makeNode("cast") {
            loc(exp)
            visit(exp.expression)
            literal(exp.type)
            literal(exp.isCoerce())
        }
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
    private static final ClassNode BUIDER_TYPE = ClassHelper.makeCached(Builder.class);
    private static final ClassNode METHOD_LOCATION_TYPE = ClassHelper.makeCached(MethodLocation.class);

    private static final VariableExpression BUILDER = new VariableExpression("b",BUILDER_TYPE); // new PropertyExpression(new ClassExpression(BUILDER_TYPE), "INSTANCE")
    private static final VariableExpression THIS = new VariableExpression("this");

    /**
     * Closure's default "it" parameter.
     */
    private static final Parameter IT = new Parameter(ClassHelper.OBJECT_TYPE, "it", ConstantExpression.NULL);

    private static final int PRIVATE_STATIC_FINAL = Modifier.STATIC | Modifier.PRIVATE | Modifier.FINAL
}
