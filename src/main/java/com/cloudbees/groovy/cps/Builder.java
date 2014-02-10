package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.AssignmentBlock;
import com.cloudbees.groovy.cps.impl.BlockScopeEnv;
import com.cloudbees.groovy.cps.impl.BreakBlock;
import com.cloudbees.groovy.cps.impl.ConstantBlock;
import com.cloudbees.groovy.cps.impl.ContinueBlock;
import com.cloudbees.groovy.cps.impl.ExcrementOperatorBlock;
import com.cloudbees.groovy.cps.impl.ForInLoopBlock;
import com.cloudbees.groovy.cps.impl.ForLoopBlock;
import com.cloudbees.groovy.cps.impl.FunctionCallBlock;
import com.cloudbees.groovy.cps.impl.IfBlock;
import com.cloudbees.groovy.cps.impl.LocalVariableBlock;
import com.cloudbees.groovy.cps.impl.LogicalOpBlock;
import com.cloudbees.groovy.cps.impl.PropertyAccessBlock;
import com.cloudbees.groovy.cps.impl.TryBlockEnv;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.cloudbees.groovy.cps.Block.*;
import static java.util.Arrays.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class Builder {
    private static final Block NULL = new ConstantBlock(null);
    private static final LValueBlock THIS = new LocalVariableBlock("this");

    public Block null_() {
        return NULL;
    }

    public Block constant(Object o) {
        return new ConstantBlock(o);
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
     * { ... }
     */
    public Block block(Block... bodies) {
        if (bodies.length==0)   return NULL;

        Block e = bodies[0];
        for (int i=1; i<bodies.length; i++)
            e = sequence(e,bodies[i]);

        return blockScoped(e);
    }

    /**
     * Creates a block scope of variables around the given expression
     */
    private Block blockScoped(final Block exp) {
        return new Block() {
            public Next eval(Env _e, final Continuation k) {
                final Env e = new BlockScopeEnv(_e); // block statement creates a new scope

                return new Next(exp,e,k);
            }
        };
    }

    /**
     * Like {@link #block(Block...)} but it doesn't create a new scope.
     *
     */
    public Block sequence(Block... bodies) {
        if (bodies.length==0)   return NULL;

        Block e = bodies[0];
        for (int i=1; i<bodies.length; i++)
            e = sequence(e,bodies[i]);

        return e;
    }

    public Block sequence(final Block exp1, final Block exp2) {
        return new Block() {
            public Next eval(final Env e, final Continuation k) {
                return new Next(exp1,e,new Continuation() {
                    public Next receive(Object __) {
                        return new Next(exp2,e,k);
                    }
                });
            }
        };
    }

    public Block sequence(Block b) {
        return b;
    }

    public LValueBlock localVariable(String name) {
        return new LocalVariableBlock(name);
    }

    public Block setLocalVariable(final String name, final Block rhs) {
        return assign(localVariable(name),rhs);
    }

    public Block declareVariable(final Class type, final String name) {
        return new Block() {
            public Next eval(final Env e, final Continuation k) {
                e.declareVariable(type,name);
                e.setLocalVariable(name,defaultPrimitiveValue.get(type));
                return k.receive(null);
            }
        };
    }

    public Block declareVariable(Class type, String name, Block init) {
        return sequence(
            declareVariable(type,name),
            setLocalVariable(name, init));
    }


    public Block this_() {
        return THIS;
    }

    /**
     * Assignment operator to a local variable, such as "x += 3"
     */
    public Block localVariableAssignOp(String name, String operator, Block rhs) {
        return setLocalVariable(name, functionCall(localVariable(name), operator, rhs));
    }

    /**
     * if (...) { ... } else { ... }
     */
    public Block if_(Block cond, Block then, Block els) {
        return new IfBlock(cond,then,els);
    }

    public Block if_(Block cond, Block then) {
        return if_(cond, then, NOOP);
    }

    /**
     * for (e1; e2; e3) { ... }
     */
    public Block forLoop(String label, Block e1, Block e2, Block e3, Block body) {
        return new ForLoopBlock(label, e1,e2,e3,body);
    }

    /**
     * for (x in col) { ... }
     */
    public Block forInLoop(String label, Class type, String variable, Block collection, Block body) {
        return new ForInLoopBlock(label,type,variable,collection,body);
    }

    public Block break_(String label) {
        if (label==null)    return BreakBlock.INSTANCE;
        return new BreakBlock(label);
    }

    public Block continue_(String label) {
        if (label==null)    return ContinueBlock.INSTANCE;
        return new ContinueBlock(label);
    }

    public Block tryCatch(Block body, CatchExpression... catches) {
        return tryCatch(body, asList(catches));
    }

    /**
     * try {
     *     ...
     * } catch (T v) {
     *     ...
     * } catch (T v) {
     *     ...
     * }
     */
    public Block tryCatch(final Block body, final List<CatchExpression> catches) {
        return new Block() {
            public Next eval(final Env e, final Continuation k) {
                final TryBlockEnv f = new TryBlockEnv(e);
                for (final CatchExpression c : catches) {
                    f.addHandler(c.type, new Continuation() {
                        public Next receive(Object t) {
                            BlockScopeEnv b = new BlockScopeEnv(e);
                            b.declareVariable(c.type, c.name);
                            b.setLocalVariable(c.name, t);

                            return new Next(c.handler, b, k);
                        }
                    });
                }

                // evaluate the body with the new environment
                return new Next(body,f,k);
            }
        };
    }

    /**
     * throw exp;
     */
    public Block throw_(final Block exp) {
        return new Block() {
            public Next eval(final Env e, Continuation k) {
                return new Next(exp,e,new Continuation() {
                    public Next receive(Object t) {
                        if (t==null) {
                            t = new NullPointerException();
                        }
                        // TODO: fake the stack trace information
                        // TODO: what if 't' is not Throwable?

                        Continuation v = e.getExceptionHandler(Throwable.class.cast(t).getClass());
                        return v.receive(t);
                    }
                });
            }
        };
    }

    public Block staticCall(Class lhs, String name, Block... argExps) {
        return functionCall(constant(lhs),name,argExps);
    }

    public Block plus(Block lhs, Block rhs) {
        return functionCall(lhs,"plus",rhs);
    }

    public Block plusEqual(LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(lhs,rhs, "plus");
    }

    public Block minus(Block lhs, Block rhs) {
        return functionCall(lhs,"minus",rhs);
    }

    public Block multiply(Block lhs, Block rhs) {
        return functionCall(lhs,"multiply",rhs);
    }

    public Block div(Block lhs, Block rhs) {
        return functionCall(lhs,"div",rhs);
    }

    public Block intdiv(Block lhs, Block rhs) {
        return functionCall(lhs,"intdiv",rhs);
    }

    public Block mod(Block lhs, Block rhs) {
        return functionCall(lhs,"mod",rhs);
    }

    public Block power(Block lhs, Block rhs) {
        return functionCall(lhs,"power",rhs);
    }

    public Block compareEqual(Block lhs, Block rhs) {
        return staticCall(ScriptBytecodeAdapter.class, "compareEqual", lhs, rhs);
    }

    public Block compareNotEqual(Block lhs, Block rhs) {
        return staticCall(ScriptBytecodeAdapter.class, "compareNotEqual", lhs, rhs);
    }

    public Block compareTo(Block lhs, Block rhs) {
        return staticCall(ScriptBytecodeAdapter.class,"compareTo",lhs,rhs);
    }

    public Block lessThan(Block lhs, Block rhs) {
        return staticCall(ScriptBytecodeAdapter.class,"compareLessThan",lhs,rhs);
    }

    public Block lessThanEqual(Block lhs, Block rhs) {
        return staticCall(ScriptBytecodeAdapter.class,"compareLessThanEqual",lhs,rhs);
    }

    public Block greaterThan(Block lhs, Block rhs) {
        return staticCall(ScriptBytecodeAdapter.class,"compareGreaterThan",lhs,rhs);
    }

    public Block greaterThanEqual(Block lhs, Block rhs) {
        return staticCall(ScriptBytecodeAdapter.class,"compareGreaterThanEqual",lhs,rhs);
    }

    /**
     * lhs && rhs
     */
    public Block logicalAnd(Block lhs, Block rhs) {
        return new LogicalOpBlock(lhs,rhs,true);
    }

    /**
     * lhs || rhs
     */
    public Block logicalOr(Block lhs, Block rhs) {
        return new LogicalOpBlock(lhs,rhs,false);
    }

    public Block bitwiseAnd(Block lhs, Block rhs) {
        return functionCall(lhs,"and",rhs);
    }

    public Block bitwiseOr(Block lhs, Block rhs) {
        return functionCall(lhs,"or",rhs);
    }

    public Block bitwiseXor(Block lhs, Block rhs) {
        return functionCall(lhs,"xor",rhs);
    }

    /**
     * ++x
     */
    public Block prefixInc(LValueBlock body) {
        return new ExcrementOperatorBlock("next",true,body);
    }

    /**
     * --x
     */
    public Block prefixDec(LValueBlock body) {
        return new ExcrementOperatorBlock("previous",true,body);
    }

    /**
     * x++
     */
    public Block postfixInc(LValueBlock body) {
        return new ExcrementOperatorBlock("next",false,body);
    }

    /**
     * x--
     */
    public Block postfixDec(LValueBlock body) {
        return new ExcrementOperatorBlock("previous",false,body);
    }

    /**
     * LHS.name(...)
     */
    public Block functionCall(Block lhs, String name, Block... argExps) {
        return new FunctionCallBlock(lhs,constant(name),argExps);
    }

    public Block functionCall(Block lhs, Block name, Block... argExps) {
        return new FunctionCallBlock(lhs,name,argExps);
    }

    public Block assign(LValueBlock lhs, Block rhs) {
        return new AssignmentBlock(lhs,rhs, null);
    }

    public LValueBlock property(Block lhs, String property) {
        return property(lhs, constant(property));
    }

    public LValueBlock property(Block lhs, Block property) {
        return new PropertyAccessBlock(lhs,property);
    }

    public Block setProperty(Block lhs, String property, Block rhs) {
        return setProperty(lhs, constant(property), rhs);
    }

    public Block setProperty(Block lhs, Block property, Block rhs) {
        return assign(property(lhs, property), rhs);
    }

    /**
     * Object instantiation.
     */
    public Block new_(Class type, Block... argExps) {
        return new_(constant(type),argExps);
    }

    public Block new_(Block type, Block... argExps) {
        return new FunctionCallBlock(type,constant("<init>"),argExps);
    }

    /**
     * return exp;
     */
    public Block return_(final Block exp) {
        return new Block() {
            public Next eval(Env e, Continuation k) {
                return new Next(exp,e, e.getReturnAddress());
            }
        };
    }

    /*TODO: specify the proper owner value (to the script that includes the call site) */
    private static CallSite fakeCallSite(String method) {
        CallSiteArray csa = new CallSiteArray(Builder.class, new String[]{method});
        return csa.array[0];
    }

    /**
     * Used for building AST from transformed code.
     */
    public static Builder INSTANCE = new Builder();

    private static final Map<Class,Object> defaultPrimitiveValue = new HashMap<Class, Object>();
    static {
        defaultPrimitiveValue.put(boolean.class,false);
        defaultPrimitiveValue.put(int.class,0);
        defaultPrimitiveValue.put(long.class,0L);
        // TODO: complete the rest
    }
}
