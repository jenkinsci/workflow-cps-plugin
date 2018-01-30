package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.CpsFunction;
import com.cloudbees.groovy.cps.sandbox.Trusted;
import com.cloudbees.groovy.cps.sandbox.Untrusted;
import com.google.common.annotations.VisibleForTesting;

import java.io.Serializable;
import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nonnull;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.ast.expr.*;
import org.codehaus.groovy.ast.stmt.*;
import org.codehaus.groovy.classgen.AsmClassGenerator;
import org.codehaus.groovy.classgen.BytecodeExpression;
import org.codehaus.groovy.classgen.GeneratorContext;
import org.codehaus.groovy.classgen.Verifier;
import org.codehaus.groovy.control.CompilePhase;
import org.codehaus.groovy.control.Janitor;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.control.customizers.CompilationCustomizer;
import org.codehaus.groovy.runtime.powerassert.SourceText;
import org.codehaus.groovy.syntax.SyntaxException;
import org.codehaus.groovy.syntax.Token;
import static org.codehaus.groovy.syntax.Types.*;

/**
 * Performs CPS transformation of Groovy methods.
 *
 * <p>
 * Every method not annotated with {@link NonCPS} gets rewritten. The general
 * strategy of CPS transformation is as follows:
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
 * Object foo(int x, int y) {
 *   // the first part is AST of the method body
 *   // the rest (including implicit receiver argument) is actual value of arguments
 *   throw new CpsCallableInvocation(___cps___N, this, new Object[] {x, y});
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
 * That is, we transform a Groovy AST of the method body into a tree of
 * {@link Block}s by using {@link Builder}, then the method just returns this
 * function object and expect the caller to evaluate it, instead of executing
 * the method synchronously before it returns.
 *
 * <p>
 * This class achieves this transformation by implementing
 * {@link GroovyCodeVisitor} and traverse Groovy AST tree in the in-order. As we
 * traverse this tree, we produce another Groovy AST tree that invokes
 * {@link Builder}. Note that we aren't calling Builder directly here; that's
 * supposed to happen when the Groovy code under transformation actually runs.
 *
 * <p>
 * Groovy AST that calls {@link Builder} is a tree of function call, so we build
 * {@link MethodCallExpression}s in the top-down manner. We do this by
 * {@link CpsTransformer#makeNode(String, Runnable)}, which creates a call to
 * {@code Builder.xxx(...)}, then supply the closure that fills in the arguments
 * to this call by walking down the original Groovy AST tree. This walk-down is
 * done by calling {@link CpsTransformer#visit(ASTNode)} (to recursively visit
 * ASTs), or by calling {@link CpsTransformer#literal(String)} methods, which
 * generate string/class/etc literals, as sometimes {@link Builder} methods need
 * them as well.
 *
 * @author Kohsuke Kawaguchi
 */
public class CpsTransformer extends CompilationCustomizer implements GroovyCodeVisitor {

    private static final Logger LOGGER = Logger.getLogger(CpsTransformer.class.getName());

    @VisibleForTesting
    public static final AtomicLong iota = new AtomicLong();

    private SourceUnit sourceUnit;

    protected ClassNode classNode;

    protected TransformerConfiguration config = new TransformerConfiguration();

    public CpsTransformer() {
        super(CompilePhase.CANONICALIZATION);
    }

    public void setConfiguration(@Nonnull TransformerConfiguration config) {
        this.config = config;
    }

    @Override
    public void call(SourceUnit source, GeneratorContext context, ClassNode classNode) {
        if (classNode.isInterface()) {
            return; // not touching interfaces
        }
        this.sourceUnit = source;
        this.classNode = classNode;
        try {

            for (FieldNode field : new ArrayList<>(classNode.getFields())) {
                visitNontransformedField(field);
            }
            for (MethodNode method : new ArrayList<>(classNode.getMethods())) {
                visitMethod(method);
            }
            processConstructors(classNode);
            for (Statement statement : new ArrayList<>(classNode.getObjectInitializerStatements())) {
                visitNontransformedStatement(statement);
            }

            classNode.addInterface(SERIALIZABLE_TYPE);

            // groovy puts timestamp of compilation into a class file, causing serialVersionUID to change.
            // this tends to be undesirable for CPS involving persistence.
            // set the timestamp to some bogus value will prevent Verifier from adding a field that encodes
            // timestamp in the field name
            // see http://stackoverflow.com/questions/15310136/neverhappen-variable-in-compiled-classes
            if (classNode.getField(Verifier.__TIMESTAMP) == null) {
                classNode.addField(Verifier.__TIMESTAMP, Modifier.STATIC | Modifier.PRIVATE, ClassHelper.long_TYPE,
                        new ConstantExpression(0L));
            }

            classNode.addAnnotation(new AnnotationNode(WORKFLOW_TRANSFORMED_TYPE));

        } finally {
            this.sourceUnit = null;
            this.classNode = null;
            this.parent = null;
        }
    }

    protected void processConstructors(ClassNode classNode) {
        for (ConstructorNode constructor : new ArrayList<>(classNode.getDeclaredConstructors())) {
            visitNontransformedMethod(constructor);
        }
    }

    /**
     * Should this method be transformed?
     */
    protected boolean shouldBeTransformed(MethodNode node) {
        return !node.isSynthetic() &&
                !hasAnnotation(node, NonCPS.class) &&
                !hasAnnotation(node, WorkflowTransformed.class) &&
                !node.isAbstract();
    }

    boolean hasAnnotation(MethodNode node, Class<? extends Annotation> a) {
        for (AnnotationNode ann : node.getAnnotations()) {
            if (ann.getClassNode().getName().equals(a.getName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Transforms asynchronous workflow method.
     *
     * From:
     *
     * ReturnT foo( T1 arg1, T2 arg2, ...) { ... body ... }
     *
     * To:
     *
     * private static CpsFunction ___cps___N = ___cps___N();
     *
     * private static final CpsFunction ___cps___N() { return new
     * CpsFunction(['arg1','arg2','arg3',...], CPS-transformed-method-body) }
     *
     * ReturnT foo( T1 arg1, T2 arg2, ...) { throw new
     * CpsCallableInvocation(___cps___N, this, new Object[] {arg1, arg2, ...}) }
     */
    public void visitMethod(final MethodNode m) {
        if (!shouldBeTransformed(m)) {
            visitNontransformedMethod(m);
            return;
        }

        final AtomicReference<Expression> body = new AtomicReference<>();

        // transform the body
        parent = new ParentClosure() {
            @Override
            public void call(Expression e) {
                body.set(e);
            }
        };
        visitWithSafepoint(m.getCode());

        ListExpression params = new ListExpression();
        for (Parameter p : m.getParameters()) {
            params.addExpression(new ConstantExpression(p.getName()));
        }

        /*
              CpsFunction ___cps___N() {
                Builder b = new Builder(...);
                return new CpsFunction( << parameters >>, << body: AST tree building code >>);
              }
         */
        String cpsName = "___cps___" + iota.getAndIncrement();

        DeclarationExpression builderDeclaration = new DeclarationExpression(BUILDER, new Token(ASSIGN, "=", -1, -1), makeBuilder(m));
        ReturnStatement returnStatement = new ReturnStatement(new ConstructorCallExpression(FUNCTION_TYPE, new TupleExpression(params, body.get())));
        MethodNode builderMethod = m.getDeclaringClass().addMethod(cpsName, PRIVATE_STATIC_FINAL, FUNCTION_TYPE, new Parameter[0], new ClassNode[0],
                new BlockStatement(Arrays.asList(new ExpressionStatement(builderDeclaration), returnStatement), new VariableScope())
        );
        builderMethod.addAnnotation(new AnnotationNode(WORKFLOW_TRANSFORMED_TYPE));

        FieldNode f = m.getDeclaringClass().addField(cpsName, PRIVATE_STATIC_FINAL, FUNCTION_TYPE,
                new StaticMethodCallExpression(m.getDeclaringClass(), cpsName, new TupleExpression()));
//                new ConstructorCallExpression(FUNCTION_TYPE, new TupleExpression(params, body)));

        Parameter[] pms = m.getParameters();
        List<Expression> paramExpressions = new ArrayList<>(pms.length);
        for (Parameter p : pms) {
            paramExpressions.add(new VariableExpression(p));
        }
        ArrayExpression paramArray = new ArrayExpression(ClassHelper.OBJECT_TYPE, paramExpressions);
        TupleExpression args = new TupleExpression(new VariableExpression(f), THIS, paramArray);

        ConstructorCallExpression cce = new ConstructorCallExpression(CPSCALLINVK_TYPE, args);
        m.setCode(new ThrowStatement(cce));

        m.addAnnotation(new AnnotationNode(WORKFLOW_TRANSFORMED_TYPE));

        if (LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.log(Level.FINEST, "in {0} transformed {1} to {2}: throw {3} plus {4}: {5}; {6}", new Object[] {
                classNode.getName(), m.getTypeDescriptor(),
                // TODO https://github.com/apache/groovy/pull/574 m.getCode().getText() does not work well
                m.getText(), cce.getText(),
                // TODO ditto for builderMethod.getCode().getText()
                builderMethod.getText(), builderDeclaration.getText(), returnStatement.getText()
            });
        }
    }

    /**
     * Generates code that instantiates a new {@link Builder}.
     *
     * <p>
     * Hook for subtypes to tweak builder, for example to
     * {@link Builder#contextualize(com.cloudbees.groovy.cps.sandbox.CallSiteTag...)}
     *
     * <pre>
     * Builder b = new Builder(new MethodLocation(...));
     * b.withClosureType(...);
     * </pre>
     *
     * @param m Method being transformed.
     */
    protected Expression makeBuilder(MethodNode m) {
        Expression b = new ConstructorCallExpression(BUIDER_TYPE, new TupleExpression(
                new ConstructorCallExpression(METHOD_LOCATION_TYPE, new TupleExpression(
                        new ConstantExpression(m.getDeclaringClass().getName()),
                        new ConstantExpression(m.getName()),
                        new ConstantExpression(sourceUnit.getName())
                ))
        ));
        b = new MethodCallExpression(b, "withClosureType",
                new TupleExpression(new ClassExpression(config.getClosureType())));

        Class tag = getTrustTag();
        if (tag != null) {
            b = new MethodCallExpression(b, "contextualize",
                    new PropertyExpression(new ClassExpression(ClassHelper.makeCached(tag)), "INSTANCE"));
        }
        return b;
    }

    /**
     * {@link Trusted} or {@link Untrusted} tag that gets added to call site.
     *
     * @see "doc/sandbox.md"
     */
    protected Class getTrustTag() {
        return Trusted.class;
    }

    /**
     * For methods that are not CPS-transformed.
     */
    protected void visitNontransformedMethod(MethodNode m) {
    }

    protected void visitNontransformedField(FieldNode f) {
    }

    protected void visitNontransformedStatement(Statement s) {
    }

    // TODO Java 8 @FunctionalInterface, or switch to Consumer<Expression>
    protected interface ParentClosure {
        void call(Expression e);
    }

    /**
     * As we visit expressions in the method body, we convert them to the
     * {@link Builder} invocations and pass them back to this closure.
     */
    protected ParentClosure parent;

    protected void visit(ASTNode e) {
        LOGGER.log(Level.FINER, "visiting {0}:{1}", new Object[] {sourceUnit.getName(), e.getLineNumber()});
        if (e instanceof EmptyExpression) {
            // working around a bug in EmptyExpression.visit() that doesn't call any method
            visitEmptyExpression((EmptyExpression) e);
        } else if (e instanceof EmptyStatement) {
            // working around a bug in EmptyStatement.visit() that doesn't call any method
            visitEmptyStatement((EmptyStatement) e);
        } else {
            e.visit(this);
        }
    }

    protected void visit(Collection<? extends ASTNode> col) {
        for (ASTNode e : col) {
            visit(e);
        }
    }

    /**
     * Like {@link #visit(ASTNode)} but also inserts the safepoint at the top.
     */
    protected void visitWithSafepoint(final Statement st) {
        if (config.getSafepoints().isEmpty()) {
            visit(st);  // common case optimization
        } else {
            makeNode("block", new Runnable() {
                @Override
                public void run() {
                    // insert function call for each safepoint
                    for (final Safepoint s : config.getSafepoints()) {
                        makeNode("staticCall", new Runnable() {
                            @Override
                            public void run() {
                                loc(st);
                                literal(s.node);
                                literal(s.methodName);
                            }
                        });
                    }
                    visit(st);
                }
            });
        }
    }

    /**
     * Makes an AST fragment that calls {@link Builder} with specific method.
     *
     * @param methodName Method on {@link Builder} to call.
     */
    protected void makeNode(String methodName, Expression... args) {
        parent.call(new MethodCallExpression(BUILDER, methodName, makeChildren(args)));
    }

    /**
     * Makes an AST fragment that calls {@link Builder} with specific method.
     *
     * @param methodName Method on {@link Builder} to call.
     */
    protected void makeNode(String methodName, Runnable body) {
        parent.call(new MethodCallExpression(BUILDER, methodName, makeChildren(body)));
    }

    /**
     * Makes an AST fragment that instantiates a new instance of the given type.
     */
    protected void makeNode(ClassNode type, Expression... args) {
        parent.call(new ConstructorCallExpression(type, makeChildren(args)));
    }

    /**
     * Makes an AST fragment that instantiates a new instance of the given type.
     */
    protected void makeNode(ClassNode type, Runnable body) {
        parent.call(new ConstructorCallExpression(type, makeChildren(body)));
    }

    /**
     * Shorthand for {@link TupleExpression#TupleExpression(Expression[])}.
     */
    protected TupleExpression makeChildren(Expression... args) {
        return new TupleExpression(args);
    }

    /**
     * Given closure, package them up into a tuple.
     */
    protected TupleExpression makeChildren(Runnable body) {
        final List<Expression> argExps = new ArrayList<>();
        ParentClosure old = parent;
        try {
            parent = new ParentClosure() {
                @Override
                public void call(Expression e) {
                    argExps.add(e);
                }
            };
            body.run(); // evaluate arguments
            return new TupleExpression(argExps);
        } finally {
            parent = old;
        }
    }

    protected void loc(ASTNode e) {
        literal(e.getLineNumber());
    }

    /**
     * Used in the closure block of {@link #makeNode(String, Runnable)} to create
     * a literal string argument.
     */
    protected void literal(String s) {
        parent.call(new ConstantExpression(s));
    }

    protected void literal(ClassNode c) {
        parent.call(new ClassExpression(c));
    }

    protected void literal(int n) {
        parent.call(new ConstantExpression(n, true));
    }

    protected void literal(boolean b) {
        parent.call(new ConstantExpression(b, true));
    }

    void visitEmptyExpression(EmptyExpression e) {
        makeNode("noop");
    }

    void visitEmptyStatement(EmptyStatement e) {
        makeNode("noop");
    }

    @Override
    public void visitMethodCallExpression(final MethodCallExpression call) {
        makeNode("functionCall", new Runnable() {
            @Override
            public void run() {
                loc(call);

                // isImplicitThis==true even when objectExpression is not 'this'.
                // See InvocationWriter.makeCall,
                if (call.isImplicitThis() && AsmClassGenerator.isThisExpression(call.getObjectExpression())) {
                    makeNode("javaThis_");
                } else {
                    visit(call.getObjectExpression());
                }
                if (call.isSpreadSafe()) {
                    throw new UnsupportedOperationException("spread not yet supported in " + call.getText()); // TODO will require safepoints
                }
                visit(call.getMethod());
                literal(call.isSafe());
                visit(((TupleExpression) call.getArguments()).getExpressions());
            }
        });
    }

    @Override
    public void visitBlockStatement(final BlockStatement b) {
        makeNode("block", new Runnable() {
            @Override
            public void run() {
                visit(b.getStatements());
            }
        });
    }

    @Override
    public void visitForLoop(final ForStatement forLoop) {
        if (ForStatement.FOR_LOOP_DUMMY.equals(forLoop.getVariable())) {
            // for ( e1; e2; e3 ) { ... }
            final ClosureListExpression loop = (ClosureListExpression) forLoop.getCollectionExpression();
            assert loop.getExpressions().size() == 3;

            makeNode("forLoop", new Runnable() {
                @Override
                public void run() {
                    literal(forLoop.getStatementLabel());
                    visit(loop.getExpressions());
                    visitWithSafepoint(forLoop.getLoopBlock());
                }
            });
        } else {
            // for (x in col) { ... }
            makeNode("forInLoop", new Runnable() {
                @Override
                public void run() {
                    loc(forLoop);
                    literal(forLoop.getStatementLabel());
                    literal(forLoop.getVariableType());
                    literal(forLoop.getVariable().getName());
                    visit(forLoop.getCollectionExpression());
                    visitWithSafepoint(forLoop.getLoopBlock());
                }
            });
        }
    }

    @Override
    public void visitWhileLoop(final WhileStatement loop) {
        makeNode("while_", new Runnable() {
            @Override
            public void run() {
                literal(loop.getStatementLabel());
                visit(loop.getBooleanExpression());
                visitWithSafepoint(loop.getLoopBlock());
            }
        });
    }

    @Override
    public void visitDoWhileLoop(final DoWhileStatement loop) {
        makeNode("doWhile", new Runnable() {
            @Override
            public void run() {
                literal(loop.getStatementLabel());
                visit(loop.getBooleanExpression());
                visitWithSafepoint(loop.getLoopBlock());
            }
        });
    }

    @Override
    public void visitIfElse(final IfStatement stmt) {
        makeNode("if_", new Runnable() {
            @Override
            public void run() {
                visit(stmt.getBooleanExpression());
                visit(stmt.getIfBlock());
                visit(stmt.getElseBlock());
            }
        });
    }

    @Override
    public void visitExpressionStatement(ExpressionStatement statement) {
        visit(statement.getExpression());
    }

    @Override
    public void visitReturnStatement(final ReturnStatement statement) {
        makeNode("return_", new Runnable() {
            @Override
            public void run() {
                visit(statement.getExpression());
            }
        });
    }

    @Override
    public void visitAssertStatement(final AssertStatement statement) {
        Janitor j = new Janitor();
        final String text = new SourceText(statement, sourceUnit, j).getNormalizedText();
        j.cleanup();

        makeNode("assert_", new Runnable() {
            @Override
            public void run() {
                visit(statement.getBooleanExpression());
                visit(statement.getMessageExpression());
                literal(text);
            }
        });
    }

    @Override
    public void visitTryCatchFinally(final TryCatchStatement stmt) {
        makeNode("tryCatch", new Runnable() {
            @Override
            public void run() {
                visit(stmt.getTryStatement());
                visit(stmt.getFinallyStatement());
                visit(stmt.getCatchStatements());
            }
        });
    }

    @Override
    public void visitSwitch(final SwitchStatement stmt) {
        makeNode("switch_", new Runnable() {
            @Override
            public void run() {
                literal(stmt.getStatementLabel());
                visit(stmt.getExpression());
                visit(stmt.getDefaultStatement());
                visit(stmt.getCaseStatements());
            }
        });
    }

    @Override
    public void visitCaseStatement(final CaseStatement stmt) {
        makeNode("case_", new Runnable() {
            @Override
            public void run() {
                loc(stmt);
                visit(stmt.getExpression());
                visit(stmt.getCode());
            }
        });
    }

    @Override
    public void visitBreakStatement(BreakStatement statement) {
        makeNode("break_", new ConstantExpression(statement.getLabel()));
    }

    @Override
    public void visitContinueStatement(ContinueStatement statement) {
        makeNode("continue_", new ConstantExpression(statement.getLabel()));
    }

    @Override
    public void visitThrowStatement(final ThrowStatement st) {
        makeNode("throw_", new Runnable() {
            @Override
            public void run() {
                loc(st);
                visit(st.getExpression());
            }
        });
    }

    @Override
    public void visitSynchronizedStatement(SynchronizedStatement statement) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitCatchStatement(final CatchStatement stmt) {
        makeNode(CATCH_EXPRESSION_TYPE, new Runnable() {
            @Override
            public void run() {
                literal(stmt.getExceptionType());
                literal(stmt.getVariable().getName());
                visit(stmt.getCode());
            }
        });
    }

    @Override
    public void visitStaticMethodCallExpression(final StaticMethodCallExpression exp) {
        makeNode("staticCall", new Runnable() {
            @Override
            public void run() {
                loc(exp);
                literal(exp.getOwnerType());
                literal(exp.getMethod());
                visit(((TupleExpression) exp.getArguments()).getExpressions());
            }
        });
    }

    @Override
    public void visitConstructorCallExpression(final ConstructorCallExpression call) {
        makeNode("new_", new Runnable() {
            @Override
            public void run() {
                loc(call);
                literal(call.getType());
                visit(((TupleExpression) call.getArguments()).getExpressions());
            }
        });
    }

    @Override
    public void visitTernaryExpression(final TernaryExpression exp) {
        makeNode("ternaryOp", new Runnable() {
            @Override
            public void run() {
                visit(exp.getBooleanExpression());
                visit(exp.getTrueExpression());
                visit(exp.getFalseExpression());
            }
        });
    }

    @Override
    public void visitShortTernaryExpression(final ElvisOperatorExpression exp) {
        makeNode("elvisOp", new Runnable() {
            @Override
            public void run() {
                visit(exp.getBooleanExpression());
                visit(exp.getFalseExpression());
            }
        });
    }

    // Constants from Token.type to a method on Builder
    private static final Map<Integer, String> BINARY_OP_TO_BUILDER_METHOD = new HashMap<>();
    static {
        BINARY_OP_TO_BUILDER_METHOD.put(COMPARE_EQUAL, "compareEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(COMPARE_NOT_EQUAL, "compareNotEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(COMPARE_TO, "compareTo");
        BINARY_OP_TO_BUILDER_METHOD.put(COMPARE_GREATER_THAN, "greaterThan");
        BINARY_OP_TO_BUILDER_METHOD.put(COMPARE_GREATER_THAN_EQUAL, "greaterThanEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(COMPARE_LESS_THAN, "lessThan");
        BINARY_OP_TO_BUILDER_METHOD.put(COMPARE_LESS_THAN_EQUAL, "lessThanEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(LOGICAL_AND, "logicalAnd");
        BINARY_OP_TO_BUILDER_METHOD.put(LOGICAL_OR, "logicalOr");
        BINARY_OP_TO_BUILDER_METHOD.put(BITWISE_AND, "bitwiseAnd");
        BINARY_OP_TO_BUILDER_METHOD.put(BITWISE_AND_EQUAL, "bitwiseAndEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(BITWISE_OR, "bitwiseOr");
        BINARY_OP_TO_BUILDER_METHOD.put(BITWISE_OR_EQUAL, "bitwiseOrEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(BITWISE_XOR, "bitwiseXor");
        BINARY_OP_TO_BUILDER_METHOD.put(BITWISE_XOR_EQUAL, "bitwiseXorEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(PLUS, "plus");
        BINARY_OP_TO_BUILDER_METHOD.put(PLUS_EQUAL, "plusEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(MINUS, "minus");
        BINARY_OP_TO_BUILDER_METHOD.put(MINUS_EQUAL, "minusEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(MULTIPLY, "multiply");
        BINARY_OP_TO_BUILDER_METHOD.put(MULTIPLY_EQUAL, "multiplyEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(DIVIDE, "div");
        BINARY_OP_TO_BUILDER_METHOD.put(DIVIDE_EQUAL, "divEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(INTDIV, "intdiv");
        BINARY_OP_TO_BUILDER_METHOD.put(INTDIV_EQUAL, "intdivEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(MOD, "mod");
        BINARY_OP_TO_BUILDER_METHOD.put(MOD_EQUAL, "modEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(POWER, "power");
        BINARY_OP_TO_BUILDER_METHOD.put(POWER_EQUAL, "powerEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(EQUAL, "assign");
        BINARY_OP_TO_BUILDER_METHOD.put(KEYWORD_INSTANCEOF, "instanceOf");
        BINARY_OP_TO_BUILDER_METHOD.put(LEFT_SQUARE_BRACKET, "array");

        BINARY_OP_TO_BUILDER_METHOD.put(LEFT_SHIFT, "leftShift");
        BINARY_OP_TO_BUILDER_METHOD.put(LEFT_SHIFT_EQUAL, "leftShiftEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(RIGHT_SHIFT, "rightShift");
        BINARY_OP_TO_BUILDER_METHOD.put(RIGHT_SHIFT_EQUAL, "rightShiftEqual");
        BINARY_OP_TO_BUILDER_METHOD.put(RIGHT_SHIFT_UNSIGNED, "rightShiftUnsigned");
        BINARY_OP_TO_BUILDER_METHOD.put(RIGHT_SHIFT_UNSIGNED_EQUAL, "rightShiftUnsignedEqual");

        BINARY_OP_TO_BUILDER_METHOD.put(FIND_REGEX, "findRegex");
        BINARY_OP_TO_BUILDER_METHOD.put(MATCH_REGEX, "matchRegex");
        BINARY_OP_TO_BUILDER_METHOD.put(KEYWORD_IN, "isCase");
    }

    private void multipleAssignment(final Expression parentExpression,
                                    final TupleExpression tuple,
                                    final Expression rhs) {
        List<Expression> tupleExpressions = tuple.getExpressions();

        for (int i = 0, tupleExpressionsSize = tupleExpressions.size(); i < tupleExpressionsSize; i++) {
            final Expression tupleExpression = tupleExpressions.get(i);
            final Expression index = new ConstantExpression(i, true);
            // def (a, b, c) = [1, 2] is allowed - c will just be null in that scenario.
            // def (a, b) = [1, 2, 3] is allowed as well - 3 is just discarded.
            // def (a, b) = 4 will error due to Integer.getAt(int) not being a thing
            // def (a, b) = "what" is allowed - a will equal 'w', and b will equal 'h'
            makeNode("assign", new Runnable() {
                @Override
                public void run() {
                    loc(parentExpression);
                    visit(tupleExpression);
                    makeNode("array", new Runnable() {
                        @Override
                        public void run() {
                            loc(rhs);
                            visit(rhs);
                            makeNode("constant", index);
                        }
                    });
                }
            });
        }
    }

    /**
     * @see
     * org.codehaus.groovy.classgen.asm.BinaryExpressionHelper#eval(BinaryExpression)
     */
    @Override
    public void visitBinaryExpression(final BinaryExpression exp) {
        String name = BINARY_OP_TO_BUILDER_METHOD.get(exp.getOperation().getType());
        if (name != null) {
            if (name.equals("assign") &&
                    exp.getLeftExpression() instanceof TupleExpression) {
                multipleAssignment(exp,
                        (TupleExpression)exp.getLeftExpression(),
                        exp.getRightExpression());
            } else {
                makeNode(name, new Runnable() {
                    @Override
                    public void run() {
                        loc(exp);
                        visit(exp.getLeftExpression());
                        visit(exp.getRightExpression());
                    }
                });
            }
            return;
        }

        throw new UnsupportedOperationException("Operation: " + exp.getOperation() + " not supported");
    }

    @Override
    public void visitPrefixExpression(final PrefixExpression exp) {
        makeNode("prefix" + prepostfixOperatorSuffix(exp.getOperation()), new Runnable() {
            @Override
            public void run() {
                loc(exp);
                visit(exp.getExpression());
            }
        });
    }

    @Override
    public void visitPostfixExpression(final PostfixExpression exp) {
        makeNode("postfix" + prepostfixOperatorSuffix(exp.getOperation()), new Runnable() {
            @Override
            public void run() {
                loc(exp);
                visit(exp.getExpression());
            }
        });
    }

    protected String prepostfixOperatorSuffix(Token operation) {
        switch (operation.getType()) {
        case PLUS_PLUS:
            return "Inc";
        case MINUS_MINUS:
            return "Dec";
        default:
            throw new UnsupportedOperationException("Unknown operator:" + operation.getText());
        }
    }

    @Override
    public void visitBooleanExpression(BooleanExpression exp) {
        visit(exp.getExpression());
    }

    @Override
    public void visitClosureExpression(final ClosureExpression exp) {
        makeNode("closure", new Runnable() {
            @Override
            public void run() {
                loc(exp);

                ListExpression types;
                ListExpression params;

                // the interpretation of the 'parameters' is messed up. According to ClosureWriter,
                // when the user explicitly defines no parameter "{ -> foo() }" then this is null,
                // when the user doesn't define any parameter explicitly { foo() }, then this is empty,
                if (exp.getParameters() == null) {
                    types = new ListExpression(Collections.EMPTY_LIST);
                    params = new ListExpression(Collections.EMPTY_LIST);
                } else if (exp.getParameters().length == 0) {
                    types = new ListExpression(Collections.<Expression>singletonList(new ClassExpression(OBJECT_TYPE)));
                    params = new ListExpression(Collections.<Expression>singletonList(new ConstantExpression("it")));
                } else {
                    Parameter[] paramArray = exp.getParameters();
                    List<Expression> typesList = new ArrayList<Expression>(paramArray.length);
                    List<Expression> paramsList = new ArrayList<Expression>(paramArray.length);
                    for (Parameter p : paramArray) {
                        typesList.add(new ClassExpression(p.getType()));
                        paramsList.add(new ConstantExpression(p.getName()));
                    }
                    types = new ListExpression(typesList);
                    params = new ListExpression(paramsList);
                }
                parent.call(types);
                parent.call(params);
                visitWithSafepoint(exp.getCode());
            }
        });
    }

    @Override
    public void visitTupleExpression(TupleExpression expression) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitMapExpression(final MapExpression exp) {
        if (exp.getMapEntryExpressions().size() > 125) {
            sourceUnit.addError(new SyntaxException("Map expressions can only contain up to 125 entries",
                    exp.getLineNumber(), exp.getColumnNumber()));
        } else {
            makeNode("map", new Runnable() {
                @Override
                public void run() {
                    for (MapEntryExpression e : exp.getMapEntryExpressions()) {
                        visit(e.getKeyExpression());
                        visit(e.getValueExpression());
                    }
                }
            });
        }
    }

    @Override
    public void visitMapEntryExpression(MapEntryExpression expression) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitListExpression(final ListExpression exp) {
        if (exp.getExpressions().size() > 250) {
            sourceUnit.addError(new SyntaxException("List expressions can only contain up to 250 elements",
                    exp.getLineNumber(), exp.getColumnNumber()));
        } else {
            makeNode("list", new Runnable() {
                @Override
                public void run() {
                    visit(exp.getExpressions());
                }
            });
        }
    }

    @Override
    public void visitRangeExpression(final RangeExpression exp) {
        makeNode("range", new Runnable() {
            @Override
            public void run() {
                loc(exp);
                visit(exp.getFrom());
                visit(exp.getTo());
                literal(exp.isInclusive());
            }
        });
    }

    @Override
    public void visitPropertyExpression(final PropertyExpression exp) {
        // TODO: spread
        if (exp.getObjectExpression() instanceof VariableExpression && ((VariableExpression) exp.getObjectExpression()).isThisExpression() &&
                exp.getProperty() instanceof ConstantExpression && classNode.getSetterMethod("set" + Verifier.capitalize((String) ((ConstantExpression) exp.getProperty()).getValue()), false) != null) {
            makeNode("attribute", new Runnable() {
                @Override
                public void run() {
                    loc(exp);
                    visit(exp.getObjectExpression());
                    visit(exp.getProperty());
                    literal(exp.isSafe());
                }
            });
        } else {
            makeNode("property", new Runnable() {
                @Override
                public void run() {
                    loc(exp);
                    visit(exp.getObjectExpression());
                    visit(exp.getProperty());
                    literal(exp.isSafe());
                }
            });
        }
    }

    @Override
    public void visitAttributeExpression(final AttributeExpression exp) {
        // TODO: spread
        makeNode("attribute", new Runnable() {
            @Override
            public void run() {
                loc(exp);
                visit(exp.getObjectExpression());
                visit(exp.getProperty());
                literal(exp.isSafe());
            }
        });
    }

    @Override
    public void visitFieldExpression(final FieldExpression exp) {
        final FieldNode f = exp.getField();
        if (f.isStatic()) {
            makeNode("staticField", new Runnable() {
                @Override
                public void run() {
                    loc(exp);
                    literal(f.getType());
                    literal(exp.getFieldName());
                }
            });
        } else {
            makeNode("property", new Runnable() {
                @Override
                public void run() {
                    loc(exp);
                    makeNode("this_");
                    literal(exp.getFieldName());
                }
            });
        }
    }

    @Override
    public void visitMethodPointerExpression(final MethodPointerExpression exp) {
        makeNode("methodPointer", new Runnable() {
            @Override
            public void run() {
                loc(exp);
                visit(exp.getExpression());
                visit(exp.getMethodName());
            }
        });
    }

    @Override
    public void visitConstantExpression(ConstantExpression expression) {
        makeNode("constant", expression);
    }

    @Override
    public void visitClassExpression(ClassExpression expression) {
        makeNode("constant", expression);
    }

    @Override
    public void visitVariableExpression(final VariableExpression exp) {
        Variable ref = exp.getAccessedVariable();
        if (ref instanceof VariableExpression /* local variable */ ||
                 ref instanceof Parameter) {
            makeNode("localVariable", new Runnable() {
                @Override
                public void run() {
                    loc(exp);
                    literal(exp.getName());
                }
            });
        } else if (ref instanceof DynamicVariable ||
                 ref instanceof PropertyNode ||
                 ref instanceof FieldNode) {
            if (ref instanceof FieldNode && classNode.getGetterMethod("get" + Verifier.capitalize(exp.getName())) != null) {
                makeNode("attribute", new Runnable() {
                    @Override
                    public void run() {
                        loc(exp);
                        makeNode("javaThis_");
                        visit(new ConstantExpression(exp.getName()));
                        literal(false);
                    }
                });
            } else {
                makeNode("property", new Runnable() {
                    @Override
                    public void run() {
                        loc(exp);
                        makeNode("javaThis_");
                        literal(exp.getName());
                    }
                });
            }
        } else if ("this".equals(exp.getName())) {
            /* Kohsuke: TODO: I don't really understand the 'true' block of the code, so I'm missing something
                if (controller.isStaticMethod() || (!controller.getCompileStack().isImplicitThis() && controller.isStaticContext())) {
                    if (controller.isInClosure()) classNode = controller.getOutermostClass();
                    visitClassExpression(new ClassExpression(classNode));
                } else {
                    loadThis();
                }
             */
            makeNode("this_");
        } else if ("super".equals(exp.getName())) {
            makeNode("super_", new Runnable() {
                @Override
                public void run() {
                    literal(classNode);
                }
            });
        } else {
            sourceUnit.addError(new SyntaxException("Unsupported expression for CPS transformation", exp.getLineNumber(), exp.getColumnNumber()));
        }
    }

    @Override
    public void visitDeclarationExpression(final DeclarationExpression exp) {
        if (exp.isMultipleAssignmentDeclaration()) {
            // def (a,b)=list
            makeNode("sequence", new Runnable() {
                @Override
                public void run() {
                    for (Expression e : exp.getTupleExpression().getExpressions()) {
                        final VariableExpression v = (VariableExpression) e;
                        makeNode("declareVariable", new Runnable() {
                            @Override
                            public void run() {
                                literal(v.getType());
                                literal(v.getName());
                            }
                        });
                    }
                    multipleAssignment(exp,
                            exp.getTupleExpression(),
                            exp.getRightExpression());

                }
            });
        } else {
            // def x=v;
            makeNode("declareVariable", new Runnable() {
                @Override
                public void run() {
                    VariableExpression v = exp.getVariableExpression();
                    loc(exp);
                    literal(v.getType());
                    literal(v.getName());
                    visit(exp.getRightExpression()); // this will not produce anything if this is EmptyExpression
                }
            });
        }
    }

    @Override
    public void visitGStringExpression(final GStringExpression exp) {
        makeNode("gstring", new Runnable() {
            @Override
            public void run() {
                loc(exp);
                makeNode("list", new Runnable() {
                    @Override
                    public void run() {
                        visit(exp.getValues());
                    }
                });
                makeNode("list", new Runnable() {
                    @Override
                    public void run() {
                        visit(exp.getStrings());
                    }
                });
            }
        });
    }

    @Override
    public void visitArrayExpression(final ArrayExpression exp) {
        if (exp.getSizeExpression() != null) {
            // array instanation like new String[1][2][3]
            makeNode("newArray", new Runnable() {
                @Override
                public void run() {
                    loc(exp);
                    literal(exp.getElementType());
                    visit(exp.getSizeExpression());
                }
            });
        } else {
            throw new UnsupportedOperationException(exp.getText());
        }
    }

    @Override
    public void visitSpreadExpression(SpreadExpression expression) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitSpreadMapExpression(SpreadMapExpression expression) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitNotExpression(final NotExpression exp) {
        makeNode("not", new Runnable() {
            @Override
            public void run() {
                loc(exp);
                visit(exp.getExpression());
            }
        });
    }

    @Override
    public void visitUnaryMinusExpression(final UnaryMinusExpression exp) {
        makeNode("unaryMinus", new Runnable() {
            @Override
            public void run() {
                loc(exp);
                visit(exp.getExpression());
            }
        });
    }

    @Override
    public void visitUnaryPlusExpression(final UnaryPlusExpression exp) {
        makeNode("unaryPlus", new Runnable() {
            @Override
            public void run() {
                loc(exp);
                visit(exp.getExpression());
            }
        });
    }

    @Override
    public void visitBitwiseNegationExpression(final BitwiseNegationExpression exp) {
        makeNode("bitwiseNegation", new Runnable() {
            @Override
            public void run() {
                loc(exp);
                visit(exp.getExpression());
            }
        });
    }

    @Override
    public void visitCastExpression(final CastExpression exp) {
        makeNode("cast", new Runnable() {
            @Override
            public void run() {
                loc(exp);
                visit(exp.getExpression());
                literal(exp.getType());
                literal(exp.isCoerce());
                // TODO what about ignoreAutoboxing & strict?
            }
        });
    }

    @Override
    public void visitArgumentlistExpression(ArgumentListExpression expression) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitClosureListExpression(ClosureListExpression closureListExpression) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void visitBytecodeExpression(BytecodeExpression expression) {
        throw new UnsupportedOperationException();
    }

    private static final ClassNode OBJECT_TYPE = ClassHelper.makeCached(Object.class);

    private static final ClassNode FUNCTION_TYPE = ClassHelper.makeCached(CpsFunction.class);

    private static final ClassNode CATCH_EXPRESSION_TYPE = ClassHelper.makeCached(CatchExpression.class);

    private static final ClassNode BUILDER_TYPE = ClassHelper.makeCached(Builder.class);

    private static final ClassNode CPSCALLINVK_TYPE = ClassHelper.makeCached(CpsCallableInvocation.class);

    private static final ClassNode WORKFLOW_TRANSFORMED_TYPE = ClassHelper.makeCached(WorkflowTransformed.class);

    private static final ClassNode BUIDER_TYPE = ClassHelper.makeCached(Builder.class);

    private static final ClassNode METHOD_LOCATION_TYPE = ClassHelper.makeCached(MethodLocation.class);

    private static final ClassNode SERIALIZABLE_TYPE = ClassHelper.makeCached(Serializable.class);

    private static final VariableExpression BUILDER = new VariableExpression("b", BUILDER_TYPE); // new PropertyExpression(new ClassExpression(BUILDER_TYPE), "INSTANCE")

    private static final VariableExpression THIS = new VariableExpression("this");

    /**
     * Closure's default "it" parameter.
     */
    private static final Parameter IT = new Parameter(ClassHelper.OBJECT_TYPE, "it", ConstantExpression.NULL);

    private static final int PRIVATE_STATIC_FINAL = Modifier.STATIC | Modifier.PRIVATE | Modifier.FINAL;
}
