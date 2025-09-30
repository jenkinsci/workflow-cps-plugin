package com.cloudbees.groovy.cps;

import static com.cloudbees.groovy.cps.Block.*;

import com.cloudbees.groovy.cps.impl.ArrayAccessBlock;
import com.cloudbees.groovy.cps.impl.AssertBlock;
import com.cloudbees.groovy.cps.impl.AssignmentBlock;
import com.cloudbees.groovy.cps.impl.AttributeAccessBlock;
import com.cloudbees.groovy.cps.impl.BlockScopedBlock;
import com.cloudbees.groovy.cps.impl.BreakBlock;
import com.cloudbees.groovy.cps.impl.CallSiteBlock;
import com.cloudbees.groovy.cps.impl.CastBlock;
import com.cloudbees.groovy.cps.impl.ClosureBlock;
import com.cloudbees.groovy.cps.impl.ConstantBlock;
import com.cloudbees.groovy.cps.impl.ContinueBlock;
import com.cloudbees.groovy.cps.impl.CpsClosure;
import com.cloudbees.groovy.cps.impl.DoWhileBlock;
import com.cloudbees.groovy.cps.impl.ElvisBlock;
import com.cloudbees.groovy.cps.impl.ExcrementOperatorBlock;
import com.cloudbees.groovy.cps.impl.ForInLoopBlock;
import com.cloudbees.groovy.cps.impl.ForLoopBlock;
import com.cloudbees.groovy.cps.impl.FunctionCallBlock;
import com.cloudbees.groovy.cps.impl.IfBlock;
import com.cloudbees.groovy.cps.impl.JavaThisBlock;
import com.cloudbees.groovy.cps.impl.ListBlock;
import com.cloudbees.groovy.cps.impl.LocalVariableBlock;
import com.cloudbees.groovy.cps.impl.LogicalOpBlock;
import com.cloudbees.groovy.cps.impl.MapBlock;
import com.cloudbees.groovy.cps.impl.MethodPointerBlock;
import com.cloudbees.groovy.cps.impl.NewArrayBlock;
import com.cloudbees.groovy.cps.impl.NewArrayFromInitializersBlock;
import com.cloudbees.groovy.cps.impl.NotBlock;
import com.cloudbees.groovy.cps.impl.PropertyAccessBlock;
import com.cloudbees.groovy.cps.impl.ReturnBlock;
import com.cloudbees.groovy.cps.impl.SequenceBlock;
import com.cloudbees.groovy.cps.impl.SourceLocation;
import com.cloudbees.groovy.cps.impl.SpreadBlock;
import com.cloudbees.groovy.cps.impl.SpreadMapBlock;
import com.cloudbees.groovy.cps.impl.StaticFieldBlock;
import com.cloudbees.groovy.cps.impl.SuperBlock;
import com.cloudbees.groovy.cps.impl.SwitchBlock;
import com.cloudbees.groovy.cps.impl.ThrowBlock;
import com.cloudbees.groovy.cps.impl.TryCatchBlock;
import com.cloudbees.groovy.cps.impl.VariableDeclBlock;
import com.cloudbees.groovy.cps.impl.WhileBlock;
import com.cloudbees.groovy.cps.impl.YieldBlock;
import com.cloudbees.groovy.cps.sandbox.CallSiteTag;
import com.cloudbees.groovy.cps.sandbox.Invoker;
import groovy.lang.Closure;
import java.util.*;
import org.codehaus.groovy.runtime.GStringImpl;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;

/**
 * Builder pattern for constructing {@link Block}s into a tree.
 *
 * For example, to build a {@link Block} that represents "1+1", you'd call {@code plus(one(),one())}
 *
 * @author Kohsuke Kawaguchi
 */
public class Builder {
    private final MethodLocation loc;
    private Class<? extends CpsClosure> closureType;
    private final Collection<CallSiteTag> tags;

    public Builder(MethodLocation loc) {
        this.loc = loc;
        this.tags = Collections.emptySet();
    }

    private Builder(Builder parent, Collection<CallSiteTag> newTags) {
        this.loc = parent.loc;
        this.closureType = parent.closureType;
        this.tags = combine(parent.tags, newTags);
    }

    private Collection<CallSiteTag> combine(Collection<CallSiteTag> a, Collection<CallSiteTag> b) {
        if (a.isEmpty()) return b;
        if (b.isEmpty()) return a;
        Collection<CallSiteTag> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    /**
     * Overrides the actual instance type of {@link CpsClosure} to be created.
     *
     * @return 'this' object for the fluent API pattern.
     */
    public Builder withClosureType(Class<? extends CpsClosure> t) {
        closureType = t;
        return this;
    }

    /**
     * Returns a new {@link Builder} that contextualizes call sites with the given tags.
     *
     * @see Invoker#contextualize(CallSiteBlock)
     */
    public Builder contextualize(CallSiteTag... tags) {
        return new Builder(this, List.of(tags));
    }

    /**
     * Evaluate the given closure by passing this object as an argument.
     * Used to bind literal Builder to a local variable.
     */
    public Object with(Closure c) {
        return c.call(this);
    }

    private static final Block NULL = new ConstantBlock(null);
    private static final LValueBlock THIS = new LocalVariableBlock(null, "this");
    private static final Block JAVA_THIS = new JavaThisBlock();

    public Block null_() {
        return NULL;
    }

    public Block noop() {
        return NOOP;
    }

    public Block constant(Object o) {
        return new ConstantBlock(o);
    }

    public Block methodPointer(int line, Block lhs, Block methodName) {
        return new MethodPointerBlock(loc(line), lhs, methodName, tags);
    }

    public Block zero() {
        return constant(0);
    }

    public Block one() {
        return constant(1);
    }

    public Block two() {
        return constant(2);
    }

    public Block true_() {
        return constant(true);
    }

    public Block false_() {
        return constant(false);
    }

    /**
     * {@code { ... }}
     */
    public Block block(Block... bodies) {
        if (bodies.length == 0) return NULL;

        Block e = bodies[0];
        for (int i = 1; i < bodies.length; i++) e = sequence(e, bodies[i]);

        return blockScoped(e);
    }

    /**
     * Creates a block scope of variables around the given expression
     */
    private Block blockScoped(final Block exp) {
        return new BlockScopedBlock(exp);
    }

    /**
     * Like {@link #block(Block...)} but it doesn't create a new scope.
     *
     */
    public Block sequence(Block... bodies) {
        if (bodies.length == 0) return NULL;

        Block e = bodies[0];
        for (int i = 1; i < bodies.length; i++) e = sequence(e, bodies[i]);

        return e;
    }

    public Block sequence(final Block exp1, final Block exp2) {
        return new SequenceBlock(exp1, exp2);
    }

    public Block sequence(Block b) {
        return b;
    }

    public Block closure(int line, List<Class> parameterTypes, List<String> parameters, Block body) {
        return new ClosureBlock(loc(line), parameterTypes, parameters, body, closureType);
    }

    public LValueBlock localVariable(String name) {
        return new LocalVariableBlock(null, name);
    }

    public LValueBlock localVariable(int line, String name) {
        return new LocalVariableBlock(loc(line), name);
    }

    public Block setLocalVariable(int line, final String name, final Block rhs) {
        return assign(line, localVariable(line, name), rhs);
    }

    public Block declareVariable(final Class type, final String name) {
        return new VariableDeclBlock(type, name);
    }

    public Block declareVariable(int line, Class type, String name, Block init) {
        return sequence(declareVariable(type, name), setLocalVariable(line, name, init));
    }

    public Block this_() {
        return THIS; // this is 'groovyThis'
    }

    /**
     * Block that's only valid as a LHS of a method call like {@code super.foo(...)}
     */
    public Block super_(Class senderType) {
        return new SuperBlock(senderType);
    }

    /**
     * See {@link JavaThisBlock} for the discussion of {@code this} vs {@code javaThis}
     */
    public Block javaThis_() {
        return JAVA_THIS;
    }

    /**
     * Assignment operator to a local variable, such as {@code x += 3}
     */
    // TODO: I think this is only used in tests.
    public Block localVariableAssignOp(int line, String name, String operator, Block rhs) {
        return setLocalVariable(line, name, functionCall(line, localVariable(line, name), operator, rhs));
    }

    /**
     * {@code if (...) { ... } else { ... }}
     */
    public Block if_(Block cond, Block then, Block els) {
        return new IfBlock(cond, then, els);
    }

    public Block if_(Block cond, Block then) {
        return if_(cond, then, NOOP);
    }

    /**
     * {@code for (e1; e2; e3) { ... }}
     */
    public Block forLoop(String label, Block e1, Block e2, Block e3, Block body) {
        return new ForLoopBlock(label, e1, e2, e3, body);
    }

    /**
     * {@code for (x in col) { ... }}
     */
    public Block forInLoop(int line, String label, Class type, String variable, Block collection, Block body) {
        return new ForInLoopBlock(loc(line), label, type, variable, collection, body);
    }

    public Block break_(String label) {
        if (label == null) return BreakBlock.INSTANCE;
        return new BreakBlock(label);
    }

    public Block continue_(String label) {
        if (label == null) return ContinueBlock.INSTANCE;
        return new ContinueBlock(label);
    }

    public Block while_(String label, Block cond, Block body) {
        return new WhileBlock(label, cond, body);
    }

    public Block doWhile(String label, Block body, Block cond) {
        return new DoWhileBlock(label, body, cond);
    }

    public Block tryCatch(Block body, Block finally_, CatchExpression... catches) {
        return tryCatch(body, List.of(catches), finally_);
    }

    /**
     * <pre>{@code
     * try {
     *     ...
     * } catch (T v) {
     *     ...
     * } catch (T v) {
     *     ...
     * }
     * }</pre>
     */
    public Block tryCatch(final Block body, final List<CatchExpression> catches) {
        return tryCatch(body, catches, null);
    }

    public Block tryCatch(final Block body, final List<CatchExpression> catches, final Block finally_) {
        return new TryCatchBlock(catches, body, finally_);
    }

    /**
     * {@code throw exp;}
     */
    public Block throw_(int line, final Block exp) {
        return new ThrowBlock(loc(line), exp, false);
    }

    /**
     * Map literal: {@code [ a:b, c:d, e:f ] ...}
     *
     * We expect arguments to be multiple of two.
     */
    public Block map(Block... blocks) {
        return new MapBlock(blocks);
    }

    public Block map(List<Block> blocks) {
        return map(blocks.toArray(new Block[blocks.size()]));
    }

    public Block staticCall(int line, Class lhs, String name, Block... argExps) {
        return functionCall(line, constant(lhs), name, argExps);
    }

    public Block plus(int line, Block lhs, Block rhs) {
        return functionCall(line, lhs, "plus", rhs);
    }

    public Block plusEqual(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, "plus");
    }

    public Block minus(int line, Block lhs, Block rhs) {
        return functionCall(line, lhs, "minus", rhs);
    }

    public Block minusEqual(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, "minus");
    }

    public Block multiply(int line, Block lhs, Block rhs) {
        return functionCall(line, lhs, "multiply", rhs);
    }

    public Block multiplyEqual(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, "multiply");
    }

    public Block div(int line, Block lhs, Block rhs) {
        return functionCall(line, lhs, "div", rhs);
    }

    public Block divEqual(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, "div");
    }

    public Block intdiv(int line, Block lhs, Block rhs) {
        return functionCall(line, lhs, "intdiv", rhs);
    }

    public Block intdivEqual(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, "intdiv");
    }

    public Block mod(int line, Block lhs, Block rhs) {
        return functionCall(line, lhs, "mod", rhs);
    }

    public Block modEqual(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, "mod");
    }

    public Block power(int line, Block lhs, Block rhs) {
        return functionCall(line, lhs, "power", rhs);
    }

    public Block powerEqual(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, "power");
    }

    public Block unaryMinus(int line, Block lhs) {
        return staticCall(line, ScriptBytecodeAdapter.class, "unaryMinus", lhs);
    }

    public Block unaryPlus(int line, Block lhs) {
        return staticCall(line, ScriptBytecodeAdapter.class, "unaryPlus", lhs);
    }

    public Block ternaryOp(Block cond, Block trueExp, Block falseExp) {
        return if_(cond, trueExp, falseExp);
    }

    /**
     * {@code x ?: y}
     */
    public Block elvisOp(Block cond, Block falseExp) {
        return new ElvisBlock(cond, falseExp);
    }

    public Block compareEqual(int line, Block lhs, Block rhs) {
        return staticCall(line, ScriptBytecodeAdapter.class, "compareEqual", lhs, rhs);
    }

    public Block compareNotEqual(int line, Block lhs, Block rhs) {
        return staticCall(line, ScriptBytecodeAdapter.class, "compareNotEqual", lhs, rhs);
    }

    public Block compareTo(int line, Block lhs, Block rhs) {
        return staticCall(line, ScriptBytecodeAdapter.class, "compareTo", lhs, rhs);
    }

    public Block lessThan(int line, Block lhs, Block rhs) {
        return staticCall(line, ScriptBytecodeAdapter.class, "compareLessThan", lhs, rhs);
    }

    public Block lessThanEqual(int line, Block lhs, Block rhs) {
        return staticCall(line, ScriptBytecodeAdapter.class, "compareLessThanEqual", lhs, rhs);
    }

    public Block greaterThan(int line, Block lhs, Block rhs) {
        return staticCall(line, ScriptBytecodeAdapter.class, "compareGreaterThan", lhs, rhs);
    }

    public Block greaterThanEqual(int line, Block lhs, Block rhs) {
        return staticCall(line, ScriptBytecodeAdapter.class, "compareGreaterThanEqual", lhs, rhs);
    }

    /**
     * {@code lhs =~ rhs}
     */
    public Block findRegex(int line, Block lhs, Block rhs) {
        return staticCall(line, ScriptBytecodeAdapter.class, "findRegex", lhs, rhs);
    }

    /**
     * {@code lhs ==~ rhs}
     */
    public Block matchRegex(int line, Block lhs, Block rhs) {
        return staticCall(line, ScriptBytecodeAdapter.class, "matchRegex", lhs, rhs);
    }

    /**
     * {@code lhs in rhs}
     */
    public Block isCase(int line, Block lhs, Block rhs) {
        return staticCall(line, ScriptBytecodeAdapter.class, "isCase", lhs, rhs);
    }

    /**
     * {@code lhs && rhs}
     */
    public Block logicalAnd(int line, Block lhs, Block rhs) {
        return new LogicalOpBlock(lhs, rhs, true);
    }

    /**
     * {@code lhs || rhs}
     */
    public Block logicalOr(int line, Block lhs, Block rhs) {
        return new LogicalOpBlock(lhs, rhs, false);
    }

    /**
     * {@code lhs << rhs}
     */
    public Block leftShift(int line, Block lhs, Block rhs) {
        return functionCall(line, lhs, "leftShift", rhs);
    }

    /**
     * {@code lhs <<= rhs}
     */
    public Block leftShiftEqual(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, "leftShift");
    }

    /**
     * {@code lhs >> rhs}
     */
    public Block rightShift(int line, Block lhs, Block rhs) {
        return functionCall(line, lhs, "rightShift", rhs);
    }

    /**
     * {@code lhs >>= rhs}
     */
    public Block rightShiftEqual(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, "rightShift");
    }

    /**
     * {@code lhs >>> rhs}
     */
    public Block rightShiftUnsigned(int line, Block lhs, Block rhs) {
        return functionCall(line, lhs, "rightShiftUnsigned", rhs);
    }

    /**
     * {@code lhs >>>= rhs}
     */
    public Block rightShiftUnsignedEqual(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, "rightShiftUnsigned");
    }

    /**
     * {@code !b}
     */
    public Block not(int line, Block b) {
        return new NotBlock(b);
    }

    public Block bitwiseAnd(int line, Block lhs, Block rhs) {
        return functionCall(line, lhs, "and", rhs);
    }

    public Block bitwiseAndEqual(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, "and");
    }

    public Block bitwiseOr(int line, Block lhs, Block rhs) {
        return functionCall(line, lhs, "or", rhs);
    }

    public Block bitwiseOrEqual(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, "or");
    }

    public Block bitwiseXor(int line, Block lhs, Block rhs) {
        return functionCall(line, lhs, "xor", rhs);
    }

    public Block bitwiseXorEqual(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, "xor");
    }

    public Block bitwiseNegation(int line, Block b) {
        return staticCall(line, ScriptBytecodeAdapter.class, "bitwiseNegate", b);
    }

    /**
     * {@code ++x}
     */
    public Block prefixInc(int line, LValueBlock body) {
        return new ExcrementOperatorBlock(loc(line), tags, "next", true, body);
    }

    /**
     * {@code --x}
     */
    public Block prefixDec(int line, LValueBlock body) {
        return new ExcrementOperatorBlock(loc(line), tags, "previous", true, body);
    }

    /**
     * {@code x++}
     */
    public Block postfixInc(int line, LValueBlock body) {
        return new ExcrementOperatorBlock(loc(line), tags, "next", false, body);
    }

    /**
     * {@code x--}
     */
    public Block postfixDec(int line, LValueBlock body) {
        return new ExcrementOperatorBlock(loc(line), tags, "previous", false, body);
    }

    /**
     * Cast to type.
     *
     * @param coerce
     *      If true, the cast will use ScriptBytecodeAdapter.asType. If false, it will use ScriptBytecodeAdapter.castToType.
     *      Both methods are very willing to coerce their values to other types, so the name is a bit misleading.
     *      Generally speaking, Groovy will use coerce=true for casts using the "as" operator, whereas coerce=false will
     *      be used in all other cases, such as Java-syntax casts and implicit casts inserted by the Groovy runtime.
     */
    public Block cast(int line, Block block, Class type, boolean coerce) {
        return new CastBlock(loc(line), tags, block, type, false, coerce, false);
    }

    /**
     * @deprecated Just for compatibility with old scripts; prefer {@link #cast}
     */
    @Deprecated
    public Block sandboxCast(int line, Block block, Class<?> type, boolean ignoreAutoboxing, boolean strict) {
        return cast(line, block, type, true);
    }

    /**
     * @deprecated Just for compatibility with old scripts; prefer {@link #cast}
     */
    @Deprecated
    public Block sandboxCastOrCoerce(
            int line, Block block, Class<?> type, boolean ignoreAutoboxing, boolean coerce, boolean strict) {
        return cast(line, block, type, coerce);
    }

    public Block instanceOf(int line, Block value, Block type) {
        return functionCall(line, type, "isInstance", value);
    }

    /**
     * {@code LHS.name(...)}
     */
    public Block functionCall(int line, Block lhs, String name, Block... argExps) {
        return functionCall(line, lhs, constant(name), false, argExps);
    }

    public Block functionCall(int line, Block lhs, Block name, boolean safe, Block... argExps) {
        return new FunctionCallBlock(loc(line), tags, lhs, name, safe, argExps);
    }

    public Block assign(int line, LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(loc(line), tags, lhs, rhs, null);
    }

    public LValueBlock property(int line, Block lhs, String property) {
        return property(line, lhs, constant(property), false);
    }

    public LValueBlock property(int line, Block lhs, Block property, boolean safe) {
        return new PropertyAccessBlock(loc(line), tags, lhs, property, safe);
    }

    public LValueBlock array(int line, Block lhs, Block index) {
        return new ArrayAccessBlock(loc(line), tags, lhs, index);
    }

    public LValueBlock attribute(int line, Block lhs, Block property, boolean safe) {
        return new AttributeAccessBlock(loc(line), tags, lhs, property, safe);
    }

    public LValueBlock staticField(int line, Class type, String name) {
        return new StaticFieldBlock(loc(line), type, name);
    }

    public Block setProperty(int line, Block lhs, String property, Block rhs) {
        return setProperty(line, lhs, constant(property), rhs);
    }

    public Block setProperty(int line, Block lhs, Block property, Block rhs) {
        return assign(line, property(line, lhs, property, false), rhs);
    }

    /**
     * Object instantiation.
     */
    public Block new_(int line, Class type, Block... argExps) {
        return new_(line, constant(type), argExps);
    }

    public Block new_(int line, Block type, Block... argExps) {
        return new FunctionCallBlock(loc(line), tags, type, constant("<init>"), false, argExps);
    }

    /**
     * Array instantiation like {@code new String[1][5]}
     */
    public Block newArray(int line, Class type, Block... argExps) {
        return new NewArrayBlock(loc(line), type, argExps);
    }

    /**
     * Array with initializers like {@code new Object[] {1, "two"}} which exists in Java but not Groovy.
     * Only used by {@link CpsDefaultGroovyMethods} and friends.
     */
    public Block newArrayFromInitializers(Block... args) {
        return new NewArrayFromInitializersBlock(args);
    }

    /**
     * {@code return exp;}
     */
    public Block return_(final Block exp) {
        return new ReturnBlock(exp);
    }

    /**
     * {@code [a,b,c,d]} that creates a List.
     */
    public Block list(Block... args) {
        return new ListBlock(args);
    }

    /**
     * {@code x..y} or {@code x..>y} to create a range
     */
    public Block range(int line, Block from, Block to, boolean inclusive) {
        return staticCall(line, ScriptBytecodeAdapter.class, "createRange", from, to, constant(inclusive));
    }

    public Block assert_(Block cond, Block msg, String sourceText) {
        return new AssertBlock(cond, msg, sourceText);
    }

    public Block assert_(Block cond, String sourceText) {
        return assert_(cond, null_(), sourceText);
    }

    /**
     * {@code "Foo bar zot ${x}"} kind of string
     */
    public Block gstring(int line, Block listOfValues, Block listOfStrings) {
        return new_(
                line,
                GStringImpl.class,
                cast(line, listOfValues, Object[].class, true),
                cast(line, listOfStrings, String[].class, true));
    }

    /**
     * @see #case_(int, Block, Block)
     */
    public Block switch_(String label, Block switchExp, Block defaultStmt, CaseExpression... caseExps) {
        return new SwitchBlock(label, switchExp, defaultStmt, List.of(caseExps));
    }

    public CaseExpression case_(int line, Block matcher, Block body) {
        return new CaseExpression(loc(line), matcher, body);
    }

    public Block yield(Object o) {
        return new YieldBlock(o);
    }

    public Block spread(int line, Block list) {
        return new SpreadBlock(loc(line), list);
    }

    public Block spreadMap(int line, Block map) {
        return new SpreadMapBlock(loc(line), map);
    }

    private SourceLocation loc(int line) {
        return new SourceLocation(loc, line);
    }
}
