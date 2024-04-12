package com.cloudbees.groovy.cps.tool;

import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JType;
import com.sun.codemodel.JTypeVar;
import com.sun.codemodel.JVar;
import com.sun.source.tree.ArrayAccessTree;
import com.sun.source.tree.ArrayTypeTree;
import com.sun.source.tree.AssignmentTree;
import com.sun.source.tree.BinaryTree;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.BreakTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.CompoundAssignmentTree;
import com.sun.source.tree.ConditionalExpressionTree;
import com.sun.source.tree.ContinueTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ExpressionStatementTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.InstanceOfTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewArrayTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.ParameterizedTypeTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.PrimitiveTypeTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.ThrowTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.Tree.Kind;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.tree.UnaryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.tree.WildcardTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.Trees;
import com.sun.source.util.TreePath;
import groovy.lang.Closure;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.NoType;
import javax.lang.model.type.PrimitiveType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.lang.model.util.ElementScanner7;
import javax.lang.model.util.Elements;
import javax.lang.model.util.SimpleTypeVisitor8;
import javax.lang.model.util.Types;
import javax.tools.JavaCompiler.CompilationTask;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import javax.annotation.processing.Generated;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;

/**
 * Generates, for example, {@code CpsDefaultGroovyMethods} from the source code of {@code DefaultGroovyMethods}.
 */
@SuppressWarnings("Since15")
public class Translator {

    private static final Set<String> translatable;
    static {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Translator.class.getResourceAsStream("translatable.txt"), StandardCharsets.UTF_8))) {
            translatable = new HashSet<>(reader.lines().collect(Collectors.toSet()));
        } catch (IOException x) {
            throw new ExceptionInInitializerError(x);
        }
    }

    private final Types types;
    private final Elements elements;
    private final Trees trees;
    private final JavacTask javac;

    private final JCodeModel codeModel = new JCodeModel();

    // class references
    private final JClass $Caller;
    private final JClass $CpsFunction;
    private final JClass $CpsCallableInvocation;
    private final JClass $Builder;
    private final JClass $CatchExpression;
    private final DeclaredType closureType;
    private final Map<String,JClass> otherTranslated = new HashMap<>();
    
    /**
     * To allow sibling calls to overloads to be resolved properly at runtime, write the actual implementation to an overload-proof private method.
     * Example key: {@code $eachByte__byte_array__groovy_lang_Closure}
     */
    private final Map<String, ExecutableElement> overloadsResolved = new TreeMap<>();

    /**
     * Parsed source files.
     */
    private final Iterable<? extends CompilationUnitTree> parsed;

    /**
     * Parse the source code and prepare for translations.
     */
    public Translator(CompilationTask task) throws IOException {
        this.javac = (JavacTask)task;

        $Caller                = codeModel.ref("com.cloudbees.groovy.cps.impl.Caller");
        $CpsFunction           = codeModel.ref("com.cloudbees.groovy.cps.impl.CpsFunction");
        $CpsCallableInvocation = codeModel.ref("com.cloudbees.groovy.cps.impl.CpsCallableInvocation");
        $Builder               = codeModel.ref("com.cloudbees.groovy.cps.Builder");
        $CatchExpression       = codeModel.ref("com.cloudbees.groovy.cps.CatchExpression");

        this.parsed = javac.parse();

        trees = Trees.instance(javac);
        elements = javac.getElements();
        types = javac.getTypes();
        closureType = types.getDeclaredType(elements.getTypeElement(Closure.class.getName()));

        javac.analyze();
    }

    private String mangledName(ExecutableElement e) {
        StringBuilder overloadResolved = new StringBuilder("$").append(n(e));
        e.getParameters().forEach(ve -> overloadResolved.append("__").append(types.erasure(ve.asType()).toString().replace("[]", "_array").replaceAll("[^\\p{javaJavaIdentifierPart}]+", "_")));
        return overloadResolved.toString();
    }

    /**
     * Transforms a single class.
     */
    public void translate(String fqcn, String outfqcn, String sourceJarName) throws JClassAlreadyExistsException {
        final JDefinedClass $output = codeModel._class(outfqcn);
        $output.annotate(Generated.class).param("value", Translator.class.getName()).param("comments", "based on " + sourceJarName);
        $output.annotate(SuppressWarnings.class).param("value", "rawtypes");
        $output.constructor(JMod.PRIVATE);

        CompilationUnitTree dgmCut = getDefaultGroovyMethodCompilationUnitTree(parsed, fqcn);

        overloadsResolved.clear();
        TypeElement dgm = elements.getTypeElement(fqcn);
        new ElementScanner7<Void,Void>() {
            @Override
            public Void visitExecutable(ExecutableElement e, Void __) {
                if (translatable.contains(fqcn + "." + e)) {
                    overloadsResolved.put(mangledName(e), e);
                }
                // System.err.println("Not translating " + e.getAnnotationMirrors() + " " + e.getModifiers() + " " + fqcn + "." + e);
                // TODO else if it is public and has a Closure argument, translate to a form that just throws UnsupportedOperationException when called in CPS mode
                return null;
            }
        }.visitType(dgm, null);
        // TODO verify that we actually found everything listed in translatables
        overloadsResolved.forEach((overloadResolved, e) -> {
            try {
                translateMethod(dgmCut, e, $output, fqcn, overloadResolved);
            } catch (Exception x) {
                throw new RuntimeException("Unable to transform " + fqcn + "." + e, x);
            }
        });

        /*
            private static MethodLocation loc(String methodName) {
                return new MethodLocation(CpsDefaultGroovyMethods.class,methodName);
            }
        */

        JClass $MethodLocation = codeModel.ref("com.cloudbees.groovy.cps.MethodLocation");
        $output.method(JMod.PRIVATE|JMod.STATIC, $MethodLocation, "loc").tap( m -> {
            JVar $methodName = m.param(String.class, "methodName");
            m.body()._return(JExpr._new($MethodLocation).arg($output.dotclass()).arg($methodName));
        });

        // Record the fqcn we've already translated for possible use later.
        otherTranslated.put(fqcn, $output);
    }

    /**
     * @param e
     *      Method in {@code fqcn} to translate.
     */
    private void translateMethod(final CompilationUnitTree cut, ExecutableElement e, JDefinedClass $output, String fqcn, String overloadResolved) {
        String methodName = n(e);
        boolean isPublic = e.getModifiers().contains(Modifier.PUBLIC);

        JMethod delegating = $output.method(isPublic ? JMod.PUBLIC | JMod.STATIC : JMod.STATIC, (JType) null, methodName);
        JMethod m = $output.method(JMod.PRIVATE | JMod.STATIC, (JType) null, overloadResolved);

        Map<String, JTypeVar> typeVars = new HashMap<>();
        e.getTypeParameters().forEach(p -> {
            String name = n(p);
            JTypeVar typeVar = delegating.generify(name);
            JTypeVar typeVar2 = m.generify(name);
            p.getBounds().forEach(b -> {
                JClass binding = (JClass) t(b, typeVars);
                typeVar.bound(binding);
                typeVar2.bound(binding);
            });
            typeVars.put(name, typeVar);
        });
        JType type = t(e.getReturnType(), typeVars);
        delegating.type(type);
        m.type(type);

        List<JVar> delegatingParams = new ArrayList<>();
        List<JVar> params = new ArrayList<>();
        e.getParameters().forEach(p -> {
            JType paramType = t(p.asType(), typeVars);
            delegatingParams.add(e.isVarArgs() && p == e.getParameters().get(e.getParameters().size() - 1) ? delegating.varParam(paramType.elementType(), n(p)) : delegating.param(paramType, n(p)));
            params.add(m.param(paramType, n(p)));
        });

        e.getThrownTypes().forEach(ex -> {
            delegating._throws((JClass)t(ex));
            m._throws((JClass)t(ex));
        });

        boolean returnsVoid = e.getReturnType().getKind() == TypeKind.VOID;

        if (isPublic) {// preamble
            /*
                If the call to this method happen outside CPS code, execute normally via DefaultGroovyMethods
             */
                delegating.body()._if(JOp.cand(
                        JOp.not($Caller.staticInvoke("isAsynchronous").tap(inv -> {
                            inv.arg(delegatingParams.get(0));
                            inv.arg(methodName);
                            for (int i = 1; i < delegatingParams.size(); i++)
                                inv.arg(delegatingParams.get(i));
                        })),
                        JOp.not($Caller.staticInvoke("isAsynchronous")
                                .arg($output.dotclass())
                                .arg(methodName)
                                .args(params))
            ))._then().tap(blk -> {
                JClass $WhateverGroovyMethods  = codeModel.ref(fqcn);
                JInvocation forward = $WhateverGroovyMethods.staticInvoke(methodName).args(delegatingParams);

                if (returnsVoid) {
                    blk.add(forward);
                    blk._return();
                } else {
                    blk._return(forward);
                }
            });
        }

        JInvocation delegateCall = $output.staticInvoke(overloadResolved);
        if (returnsVoid) {
            delegating.body().add(delegateCall);
        } else {
            delegating.body()._return(delegateCall);
        }
        delegatingParams.forEach(delegateCall::arg);

        JVar $b = m.body().decl($Builder, "b", JExpr._new($Builder).arg(JExpr.invoke("loc").arg(methodName)).
            invoke("contextualize").arg(codeModel.ref("com.cloudbees.groovy.cps.sandbox.Trusted").staticRef("INSTANCE")));
        JInvocation f = JExpr._new($CpsFunction);

        // parameter names
        f.arg(codeModel.ref(Arrays.class).staticInvoke("asList").tap(inv -> e.getParameters().forEach(p -> inv.arg(n(p)) )));

        // translate the method body into an expression that invokes Builder
        f.arg(trees.getTree(e).getBody().accept(new SimpleTreeVisitor<JExpression,Void>() {
            private JExpression visit(Tree t) {
                if (t==null)    return JExpr._null();
                return visit(t, null);
            }

            /**
             * Maps a symbol to its source location.
             */
            private JExpression loc(Tree t) {
                long pos = trees.getSourcePositions().getStartPosition(cut, t);
                return JExpr.lit((int)cut.getLineMap().getLineNumber(pos));
            }

            @Override
            public JExpression visitWhileLoop(WhileLoopTree wt, Void __) {
                return $b.invoke("while_")
                        .arg(JExpr._null()) // TODO: label
                        .arg(visit(wt.getCondition()))
                        .arg(visit(wt.getStatement()));
            }

            @Override
            public JExpression visitMethodInvocation(MethodInvocationTree mt, Void __) {
                ExpressionTree ms = mt.getMethodSelect();
                JInvocation inv;

                if (ms.getKind() == Tree.Kind.MEMBER_SELECT) {
                    MemberSelectTree mst = (MemberSelectTree) ms;
                    // If this is a call to a static method on another class, it may be an already-translated method,
                    // in which case, we need to use that translated method, not the original. So check if the expression
                    // is an identifier, that it's not the class we're in the process of translating, and if it's one
                    // of the other known translated classes.
                    Element mstExpr = getElement(mst.getExpression());
                    if (mst.getExpression().getKind() == Tree.Kind.IDENTIFIER &&
                            !mstExpr.toString().equals(fqcn) &&
                            otherTranslated.containsKey(mstExpr.toString())) {
                        inv = $b.invoke("functionCall")
                                .arg(loc(mt))
                                .arg($b.invoke("constant").arg(
                                        otherTranslated.get(mstExpr.toString()).dotclass()))
                                .arg(n(mst.getIdentifier()));

                    } else {
                        inv = $b.invoke("functionCall")
                                .arg(loc(mt))
                                .arg(visit(mst.getExpression()))
                                .arg(n(mst.getIdentifier()));
                    }
                } else
                if (ms.getKind() == Tree.Kind.IDENTIFIER) {
                    // invocation without object selection, like  foo(bar,zot)
                    IdentifierTree it = (IdentifierTree) ms;
                    Element mse = getElement(ms);
                    Element owner = mse.getEnclosingElement();
                    if (!owner.toString().equals(fqcn)) {
                        if (otherTranslated.containsKey(owner.toString())) {
                            // static import from transformed class
                            inv = $b.invoke("functionCall")
                                    .arg(loc(mt))
                                    .arg($b.invoke("constant").arg(otherTranslated.get(owner.toString()).dotclass()))
                                    .arg(n(it));
                        } else {
                            // static import from non-transformed class
                            inv = $b.invoke("functionCall")
                                    .arg(loc(mt))
                                    .arg($b.invoke("constant").arg(t(owner.asType()).dotclass()))
                                    .arg(n(it));
                        }
                    } else {
                        // invocation on this class
                        String overloadResolved = mangledName((ExecutableElement) mse);
                        Optional<? extends Element> callSite = elements.getTypeElement(fqcn).getEnclosedElements().stream().filter(e ->
                            e.getKind() == ElementKind.METHOD && mangledName((ExecutableElement) e).equals(overloadResolved)
                        ).findAny();
                        if (callSite.isPresent()) {
                            ExecutableElement e = (ExecutableElement) callSite.get();
                            if (e.getModifiers().contains(Modifier.PUBLIC) && !e.isVarArgs() && e.getParameters().stream().noneMatch(p -> types.isAssignable(p.asType(), closureType))) {
                                // Delegate to the standard version.
                                inv = $b.invoke("staticCall")
                                    .arg(loc(mt))
                                    .arg(t(owner.asType()).dotclass())
                                    .arg(n(e));
                            } else if (overloadsResolved.containsKey(overloadResolved)) {
                                // Private, so delegate to our mangled version.
                                // TODO add a String parameter to each internal helper method for the expected methodName to pass to CpsCallableInvocation.<init>
                                // (It could be improved to take a parameter for the name under which we expect methodCall to be invoking it.
                                // Usually just `each`, but might be `$each__java_util_Iterable__groovy_lang_Closure` for the case that one DGM method is delegating to another.
                                // See comment in ContinuationGroup, where we are unable to enforce continuation name mismatches in this case.)
                                inv = $b.invoke("staticCall")
                                    .arg(loc(mt))
                                    .arg($output.dotclass())
                                    .arg(overloadResolved);
                            } else {
                                throw new IllegalStateException("Not yet translating a " + e.getModifiers() + " method; translatable.txt might need to include: " + fqcn + "." + e);
                            }
                        } else {
                            throw new IllegalStateException("Could not find self-call site " + overloadResolved + " for " + mt);
                        }
                    }
                } else {
                    // TODO: figure out what can come here
                    throw new UnsupportedOperationException(ms.toString());
                }

                mt.getArguments().forEach( a -> inv.arg(visit(a)) );
                return inv;
            }

            @Override
            public JExpression visitVariable(VariableTree vt, Void __) {
                return $b.invoke("declareVariable")
                        .arg(loc(vt))
                        .arg(cpsTypeTranslation(erasure(getPath(vt))))
                        .arg(n(vt))
                        .arg(visit(vt.getInitializer()));
            }

            @Override
            public JExpression visitIdentifier(IdentifierTree it, Void __) {
                Element ite = getElement(it);
                switch (ite.getKind()) {
                    case CLASS:
                    case INTERFACE:
                        return $b.invoke("constant").arg(t(ite.asType()).dotclass());
                    case EXCEPTION_PARAMETER:
                    case LOCAL_VARIABLE:
                    case PARAMETER:
                        return $b.invoke("localVariable").arg(n(it.getName()));
                    default:
                        throw new UnsupportedOperationException(it + " (kind " + ite.getKind() + ")");

                }
            }

            @Override
            public JExpression visitBlock(BlockTree bt, Void __) {
                JInvocation inv = $b.invoke("block");
                bt.getStatements().forEach(s -> inv.arg(visit(s)));
                return inv;
            }

            @Override
            public JExpression visitReturn(ReturnTree rt, Void __) {
                return $b.invoke("return_").arg(visit(rt.getExpression()));
            }

            /**
             * When used outside {@link MethodInvocationTree}, this is property access.
             */
            @Override
            public JExpression visitMemberSelect(MemberSelectTree mt, Void __) {
                return $b.invoke("property")
                        .arg(loc(mt))
                        .arg(visit(mt.getExpression()))
                        .arg(n(mt.getIdentifier()));
            }

            @Override
            public JExpression visitTypeCast(TypeCastTree tt, Void __) {
                return $b.invoke("cast")
                        .arg(loc(tt))
                        .arg(visit(tt.getExpression()))
                        .arg(erasure(getPath(tt.getType())).dotclass())
                        .arg(JExpr.lit(false));
            }


            @Override
            public JExpression visitIf(IfTree it, Void __) {
                JInvocation inv = $b.invoke("if_")
                        .arg(visit(it.getCondition()))
                        .arg(visit(it.getThenStatement()));
                if (it.getElseStatement()!=null)
                    inv.arg(visit(it.getElseStatement()));
                return inv;
            }

            @Override
            public JExpression visitNewClass(NewClassTree nt, Void __) {
                // TODO: outer class
                if (nt.getEnclosingExpression()!=null)
                    throw new UnsupportedOperationException();

                return $b.invoke("new_").tap(inv -> {
                    inv.arg(loc(nt));
                    inv.arg(cpsTypeTranslation(t(getElement(nt.getIdentifier()).asType())));
                    nt.getArguments().forEach( et -> inv.arg(visit(et)) );
                });
            }

            @Override
            public JExpression visitExpressionStatement(ExpressionStatementTree et, Void __) {
                return visit(et.getExpression());
            }

            @Override
            public JExpression visitLiteral(LiteralTree lt, Void __) {
                return $b.invoke("constant").arg(JExpr.literal(lt.getValue()));
            }

            @Override
            public JExpression visitParenthesized(ParenthesizedTree pt, Void __) {
                return visit(pt.getExpression());
            }

            @Override
            public JExpression visitBinary(BinaryTree bt, Void __) {
                return $b.invoke(opName(bt.getKind()))
                        .arg(loc(bt))
                        .arg(visit(bt.getLeftOperand()))
                        .arg(visit(bt.getRightOperand()));
            }

            @Override
            public JExpression visitUnary(UnaryTree ut, Void __) {
                return $b.invoke(opName(ut.getKind()))
                        .arg(loc(ut))
                        .arg(visit(ut.getExpression()));
            }

            @Override
            public JExpression visitCompoundAssignment(CompoundAssignmentTree ct, Void __) {
                return $b.invoke(opName(ct.getKind()))
                        .arg(loc(ct))
                        .arg(visit(ct.getVariable()))
                        .arg(visit(ct.getExpression()));
            }

            private String opName(Kind kind) {
                switch (kind) {
                case EQUAL_TO:              return "compareEqual";
                case NOT_EQUAL_TO:          return "compareNotEqual";
                case LESS_THAN_EQUAL:       return "lessThanEqual";
                case LESS_THAN:             return "lessThan";
                case GREATER_THAN_EQUAL:    return "greaterThanEqual";
                case GREATER_THAN:          return "greaterThan";
                case PREFIX_INCREMENT:      return "prefixInc";
                case POSTFIX_INCREMENT:     return "postfixInc";
                case POSTFIX_DECREMENT:     return "postfixDec";
                case LOGICAL_COMPLEMENT:    return "not";
                case CONDITIONAL_OR:        return "logicalOr";
                case CONDITIONAL_AND:       return "logicalAnd";
                case PLUS:                  return "plus";
                case PLUS_ASSIGNMENT:       return "plusEqual";
                case MINUS:                 return "minus";
                case MINUS_ASSIGNMENT:      return "minusEqual";
                }
                throw new UnsupportedOperationException(kind.toString());
            }

            @Override
            public JExpression visitAssignment(AssignmentTree at, Void __) {
                return $b.invoke("assign")
                        .arg(loc(at))
                        .arg(visit(at.getVariable()))
                        .arg(visit(at.getExpression()));
            }

            /**
             * This is needed to handle cases like {@code Object[].class}.
             */
            @Override
            public JExpression visitArrayType(ArrayTypeTree at, Void __) {
                if (at.getType().getKind() == Tree.Kind.IDENTIFIER) {
                    return visitIdentifier((IdentifierTree) at.getType(), __);
                } else {
                    return defaultAction(at, __);
                }
            }

            @Override
            public JExpression visitNewArray(NewArrayTree nt, Void __) {
                if (nt.getInitializers()!=null) {
                    return $b.invoke("newArrayFromInitializers").tap(inv -> nt.getInitializers().forEach(d -> inv.arg(visit(d))));
                } else {
                    return $b.invoke("newArray").tap(inv -> {
                        inv.arg(loc(nt));
                        inv.arg(t(getPath(nt.getType())).dotclass());
                        nt.getDimensions().forEach(d -> inv.arg(visit(d)));
                    });
                }
            }

            @Override
            public JExpression visitForLoop(ForLoopTree ft, Void __) {
                return $b.invoke("forLoop")
                        .arg(JExpr._null())
                        .arg($b.invoke("sequence").tap(inv -> ft.getInitializer().forEach(i -> inv.arg(visit(i)))))
                        .arg(visit(ft.getCondition()))
                        .arg($b.invoke("sequence").tap(inv -> ft.getUpdate().forEach(i -> inv.arg(visit(i)))))
                        .arg(visit(ft.getStatement()));
            }

            @Override
            public JExpression visitEnhancedForLoop(EnhancedForLoopTree et, Void __) {
                return $b.invoke("forInLoop")
                        .arg(loc(et))
                        .arg(JExpr._null())
                        .arg(erasure(getPath(et.getVariable())).dotclass())
                        .arg(n(et.getVariable()))
                        .arg(visit(et.getExpression()))
                        .arg(visit(et.getStatement()));
            }

            @Override
            public JExpression visitArrayAccess(ArrayAccessTree at, Void __) {
                return $b.invoke("array")
                        .arg(loc(at))
                        .arg(visit(at.getExpression()))
                        .arg(visit(at.getIndex()));
            }

            @Override
            public JExpression visitBreak(BreakTree node, Void __) {
                if (node.getLabel()!=null)
                    throw new UnsupportedOperationException();
                return $b.invoke("break_").arg(JExpr._null());
            }

            @Override
            public JExpression visitContinue(ContinueTree node, Void aVoid) {
                if (node.getLabel()!=null)
                    throw new UnsupportedOperationException();
                return $b.invoke("continue_").arg(JExpr._null());
            }

            @Override
            public JExpression visitInstanceOf(InstanceOfTree it, Void __) {
                return $b.invoke("instanceOf")
                        .arg(loc(it))
                        .arg(visit(it.getExpression()))
                        .arg($b.invoke("constant").arg(t(getPath(it.getType())).dotclass()));
            }

            @Override
            public JExpression visitThrow(ThrowTree tt, Void __) {
                return $b.invoke("throw_")
                        .arg(loc(tt))
                        .arg(visit(tt.getExpression()));
            }

            @Override
            public JExpression visitDoWhileLoop(DoWhileLoopTree dt, Void __) {
                return $b.invoke("doWhile")
                        .arg(JExpr._null())
                        .arg(visit(dt.getStatement()))
                        .arg(visit(dt.getCondition()));
            }

            @Override
            public JExpression visitConditionalExpression(ConditionalExpressionTree ct, Void __) {
                return $b.invoke("ternaryOp")
                        .arg(visit(ct.getCondition()))
                        .arg(visit(ct.getTrueExpression()))
                        .arg(visit(ct.getFalseExpression()));
            }

            @Override
            public JExpression visitTry(TryTree tt, Void __) {
                return $b.invoke("tryCatch")
                        .arg(visit(tt.getBlock()))
                        .arg(visit(tt.getFinallyBlock()))
                        .tap(inv ->
                            tt.getCatches().forEach(ct ->
                                JExpr._new($CatchExpression)
                                    .arg(t(trees.getPath(cut, ct.getParameter())).dotclass())
                                    .arg(n(ct.getParameter()))
                                    .arg(visit(ct.getBlock())))
                        );
            }

            @Override
            protected JExpression defaultAction(Tree node, Void aVoid) {
                throw new UnsupportedOperationException(node.toString());
            }

            private TreePath getPath(Tree node) {
                return trees.getPath(cut, node);
            }

            private Element getElement(Tree node) {
                return trees.getElement(getPath(node));
            }
        }, null));

        JVar $f = m.body().decl($CpsFunction, "f", f);
        m.body()._throw(JExpr._new($CpsCallableInvocation)
            .arg(JExpr.lit(methodName))
            .arg($f)
            .arg(JExpr._null())
            .args(params));
    }

    private CompilationUnitTree getDefaultGroovyMethodCompilationUnitTree(Iterable<? extends CompilationUnitTree> parsed, String fqcn) {
        for (CompilationUnitTree cut : parsed) {
            for (Tree t : cut.getTypeDecls()) {
                if (t.getKind() == Kind.CLASS) {
                    ClassTree ct = (ClassTree)t;
                    if (ct.getSimpleName().toString().equals(fqcn.replaceFirst("^.+[.]", ""))) { // TODO how do we get the FQCN of a ClassTree?
                        return cut;
                    }
                }
            }
        }
        throw new IllegalStateException(fqcn + " wasn't parsed");
    }


    /**
     * Convert a type representation from javac to codemodel.
     */
    private JType t(TreePath t) {
        return t.getLeaf().accept(new TypeTranslator(t.getCompilationUnit()), null);
    }

    /**
     * Converts a type representation to its erasure.
     */
    private JType erasure(TreePath t) {
        return t.getLeaf().accept(new TypeTranslator(t.getCompilationUnit()) {
            @Override
            public JType visitParameterizedType(ParameterizedTypeTree pt, Void __) {
                return visit(pt.getType());
            }

            @Override
            public JType visitWildcard(WildcardTree wt, Void __) {
                Tree b = wt.getBound();
                if (b==null)    return codeModel.ref(Object.class);
                else            return visit(b);
            }

            @Override
            public JType visitIdentifier(IdentifierTree it, Void __) {
                Element ite = trees.getElement(trees.getPath(t.getCompilationUnit(), it));
                switch (ite.getKind()) {
                    case CLASS:
                    case INTERFACE:
                        return codeModel.ref(ite.toString());
                    case TYPE_PARAMETER:
                        TypeMirror type = ite.asType();
                        if (type.getKind() == TypeKind.TYPEVAR) {
                            return t(((TypeVariable)type).getUpperBound());
                        }
                        return codeModel.ref(Object.class);
                    default:
                        throw new UnsupportedOperationException(it + " (kind " + ite.getKind() + ")");

                }
            }
        }, null);
    }

    private JType t(TypeMirror m) {
        return t(m, Collections.emptyMap());
    }

    private JType t(TypeMirror m, Map<String, JTypeVar> typeVars) {
        if (m.getKind().isPrimitive())
            return JType.parse(codeModel,m.toString());

        return m.accept(new SimpleTypeVisitor8<JType, Void>() {
            @Override
            public JType visitPrimitive(PrimitiveType t, Void __) {
                return primitive(t, t.getKind());
            }

            @Override
            public JType visitDeclared(DeclaredType t, Void __) {
                String name = n(((TypeElement) t.asElement()).getQualifiedName());
                if (name.isEmpty())
                    throw new UnsupportedOperationException("Anonymous class: "+t);
                JClass base = codeModel.ref(name);
                if (t.getTypeArguments().isEmpty())
                    return base;

                List<JClass> typeArgs = new ArrayList<>();
                t.getTypeArguments().forEach(a -> typeArgs.add((JClass) t(a, typeVars)));
                return base.narrow(typeArgs);
            }

            @Override
            public JType visitTypeVariable(TypeVariable t, Void __) {
                String name = t.asElement().getSimpleName().toString();
                JTypeVar var = typeVars.get(name);
                if (var != null) {
                    return var; // TODO bounds
                } else {
                    // TODO <T,U>with(U,groovy.lang.Closure<T>) somehow asks us to visit V, huh?
                    return t(t.getUpperBound(), typeVars);
                }
            }

            @Override
            public JType visitNoType(NoType t, Void __) {
                return primitive(t, t.getKind());
            }

            @Override
            public JType visitArray(ArrayType t, Void __) {
                return t(t.getComponentType(), typeVars).array();
            }

            @Override
            public JType visitWildcard(WildcardType t, Void aVoid) {
                if (t.getExtendsBound()!=null) {
                    return t(t.getExtendsBound(), typeVars).boxify().wildcard();
                }
                if (t.getSuperBound()!=null) {
                    throw new UnsupportedOperationException();
                }
                return codeModel.wildcard();
            }

            @Override
            protected JType defaultAction(TypeMirror e, Void __) {
                throw new UnsupportedOperationException(e.toString());
            }
        }, null);
    }

    private String n(Element e) {
        return e.getSimpleName().toString();
    }
    private String n(Name n) {
        return n.toString();
    }
    private String n(VariableTree v) {
        return n(v.getName());
    }
    private String n(IdentifierTree v) {
        return n(v.getName());
    }

    private JType primitive(Object src, TypeKind k) {
        switch (k) {
        case BOOLEAN:   return codeModel.BOOLEAN;
        case BYTE:      return codeModel.BYTE;
        case SHORT:     return codeModel.SHORT;
        case INT:       return codeModel.INT;
        case LONG:      return codeModel.LONG;
        case CHAR:      return codeModel.CHAR;
        case FLOAT:     return codeModel.FLOAT;
        case DOUBLE:    return codeModel.DOUBLE;
        case VOID:      return codeModel.VOID;
        }
        throw new UnsupportedOperationException(src.toString());
    }

    /**
     * Replaces non-serializable types with CPS-specific variants.
     *
     * @param original a {@link JType} to inspect
     * @return The {@link JType#dotclass()} for either the original {@link JType} or for the CPS equivalent.
     */
    private JExpression cpsTypeTranslation(JType original) {
        if (original.fullName().equals("org.codehaus.groovy.runtime.callsite.BooleanClosureWrapper")) {
            return codeModel.ref("com.cloudbees.groovy.cps.impl.CpsBooleanClosureWrapper").dotclass();
        } else {
            return original.dotclass();
        }
    }

    /**
     * Generate transalted result into source files.
     */
    public void generateTo(CodeWriter cw) throws IOException {
        codeModel.build(cw);
    }

    private class TypeTranslator extends SimpleTreeVisitor<JType, Void> {
        private final CompilationUnitTree cut;

        private TypeTranslator(CompilationUnitTree cut) {
            this.cut = cut;
        }

        protected JType visit(Tree t) {
            return visit(t,null);
        }

        @Override
        public JType visitVariable(VariableTree node, Void __) {
            return visit(node.getType());
        }

        @Override
        public JType visitParameterizedType(ParameterizedTypeTree pt, Void __) {
            JClass base = (JClass)visit(pt.getType());
            List<JClass> args = new ArrayList<>();
            for (Tree arg : pt.getTypeArguments()) {
                args.add((JClass)visit(arg));
            }
            return base.narrow(args);
        }

        @Override
        public JType visitIdentifier(IdentifierTree it, Void __) {
            return codeModel.ref(getElement(it).toString());
        }

        @Override
        public JType visitPrimitiveType(PrimitiveTypeTree pt, Void aVoid) {
            return primitive(pt, pt.getPrimitiveTypeKind());
        }

        @Override
        public JType visitArrayType(ArrayTypeTree at, Void __) {
            return visit(at.getType()).array();
        }

        /**
         * Nested type
         */
        @Override
        public JType visitMemberSelect(MemberSelectTree mt, Void __) {
            return t(getElement(mt).asType());
        }

        @Override
        public JType visitWildcard(WildcardTree wt, Void __) {
            Tree b = wt.getBound();
            if (b==null)    return codeModel.wildcard();
            else            return visit(b).boxify().wildcard();
        }

        @Override
        protected JType defaultAction(Tree node, Void __) {
            throw new UnsupportedOperationException(node.toString());
        }

        private Element getElement(Tree node) {
            return trees.getElement(trees.getPath(cut, node));
        }
    }
}
