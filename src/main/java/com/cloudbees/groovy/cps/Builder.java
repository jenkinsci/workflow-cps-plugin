package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.BlockScopeEnv;
import com.cloudbees.groovy.cps.impl.Constant;
import com.cloudbees.groovy.cps.impl.ForInLoopBlock;
import com.cloudbees.groovy.cps.impl.ForLoopBlock;
import com.cloudbees.groovy.cps.impl.IfBlock;
import com.cloudbees.groovy.cps.impl.LogicalOpBlock;
import com.cloudbees.groovy.cps.impl.TryBlockEnv;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.codehaus.groovy.runtime.callsite.CallSite;
import org.codehaus.groovy.runtime.callsite.CallSiteArray;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.cloudbees.groovy.cps.Block.*;
import static java.util.Arrays.*;
import static java.util.Collections.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class Builder {
    private static final Block NULL = new Constant(null);

    public Block null_() {
        return NULL;
    }

    public Block constant(Object o) {
        return new Constant(o);
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

    public Block sequence(Block... bodies) {
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

//    public Block compareLessThan(final Block lhs, final Block rhs) {
//
//    }

    public Block getLocalVariable(final String name) {
        return new Block() {
            public Next eval(Env e, Continuation k) {
                return k.receive(e.getLocalVariable(name));
            }
        };
    }

    public Block setLocalVariable(final String name, final Block rhs) {
        return new Block() {
            public Next eval(final Env e, final Continuation k) {
                return rhs.eval(e,new Continuation() {
                    public Next receive(Object o) {
                        e.setLocalVariable(name, o);
                        return k.receive(o);
                    }
                });
            }
        };
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


    public Block this_() {
        return getLocalVariable("this");
    }

    /**
     * Assignment operator to a local variable, such as "x += 3"
     */
    public Block localVariableAssignOp(String name, String operator, Block rhs) {
        return setLocalVariable(name, functionCall(getLocalVariable(name),operator,rhs));
    }

    /**
     * if (...) { ... } else { ... }
     */
    public Block if_(Block cond, Block then, Block els) {
        return new IfBlock(cond,then,els);
    }

    public Block if_(Block cond, Block then) {
        return if_(cond,then, NOOP);
    }

    /**
     * for (e1; e2; e3) { ... }
     */
    public Block forLoop(Block e1, Block e2, Block e3, Block body) {
        return new ForLoopBlock(e1,e2,e3,body);
    }

    /**
     * for (x in col) { ... }
     */
    public Block forInLoop(Class type, String variable, Block collection, Block body) {
        return new ForInLoopBlock(type,variable,collection,body);
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
        return staticCall(ScriptBytecodeAdapter.class,"compareEqual",lhs,rhs);
    }

    public Block compareNotEqual(Block lhs, Block rhs) {
        return staticCall(ScriptBytecodeAdapter.class,"compareNotEqual",lhs,rhs);
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
     * LHS.name(...)
     */
    public Block functionCall(final Block lhs, final String name, Block... argExps) {
        final CallSite callSite = fakeCallSite(name); // name is statically determined
        final Block args = evalArgs(argExps);

        return new Block() {
            public Next eval(final Env e, final Continuation k) {
                return new Next(lhs,e, new Continuation() {// evaluate lhs
                    public Next receive(final Object lhs) {
                        return args.eval(e,new Continuation() {
                            public Next receive(Object _args) {
                                List args = (List) _args;

                                Object v;
                                try {
                                    v = callSite.call(lhs, args.toArray(new Object[args.size()]));
                                } catch (Throwable t) {
                                    throw new UnsupportedOperationException(t);     // TODO: exception handling
                                }

                                if (v instanceof Function) {
                                    // if this is a workflow function, it'd return a Function object instead
                                    // of actually executing the function, so execute it in the CPS
                                    return ((Function)v).invoke(e,lhs,args,k);
                                } else {
                                    // if this was a normal function, the method had just executed synchronously
                                    return k.receive(v);
                                }
                            }
                        });
                    }
                });
            }
        };
    }

    public Block functionCall(final Block lhs, final Block name, Block... argExps) {
        if (name instanceof Constant) {
            // method name statically known. this common path enables a bit of optimization
            return functionCall(lhs,((Constant)name).value.toString(),argExps);
        }

        final Block args = evalArgs(argExps);

        // TODO: what is the correct evaluation order?

        return new Block() {
            public Next eval(final Env e, final Continuation k) {
                return new Next(lhs,e, new Continuation() {// evaluate lhs
                    public Next receive(final Object lhs) {
                        return new Next(name, e, new Continuation() {
                            public Next receive(final Object name) {
                                return args.eval(e,new Continuation() {
                                    public Next receive(Object _args) {
                                        List args = (List) _args;

                                        Object v;
                                        try {
                                            CallSite callSite = fakeCallSite(name.toString());
                                            v = callSite.call(lhs, args.toArray(new Object[args.size()]));
                                        } catch (Throwable t) {
                                            throw new UnsupportedOperationException(t);     // TODO: exception handling
                                        }

                                        if (v instanceof Function) {
                                            // if this is a workflow function, it'd return a Function object instead
                                            // of actually executing the function, so execute it in the CPS
                                            return ((Function)v).invoke(e,lhs,args,k);
                                        } else {
                                            // if this was a normal function, the method had just executed synchronously
                                            return k.receive(v);
                                        }
                                    }
                                });
                            }
                        });
                    }
                });
            }
        };
    }

//    /**
//     * name(...)
//     *
//     * TODO: is this the same as this.name(...) ? -> NO, not in closure
//     */
//    public Block functionCall(final String name, Block... argExps) {
//        final Block args = evalArgs(argExps);
//        return new Block() {
//            public Next eval(final Env e, final Continuation k) {
//                return args.eval(e,new Continuation() {
//                    public Next receive(Object args) {
//                        final Function f = e.resolveFunction(name);
//                        return f.invoke((List)args,k);
//                    }
//                });
//            }
//        };
//    }

    public Block getProperty(Block lhs, String property) {
        return getProperty(lhs,constant(property));
    }

    public Block getProperty(final Block lhs, final Block property) {
        return new Block() {
            public Next eval(final Env e, final Continuation k) {
                return lhs.eval(e,new Continuation() {
                    public Next receive(final Object lhs) {
                        return new Next(property,e,new Continuation() {
                            public Next receive(Object property) {
                                Object v;
                                try {
                                    // TODO: verify the behaviour of Groovy if the property expression evaluates to non-String
                                    v = ScriptBytecodeAdapter.getProperty(null/*Groovy doesn't use this parameter*/,
                                            lhs, (String) property);
                                } catch (Throwable t) {
                                    throw new UnsupportedOperationException(t);     // TODO: exception handling
                                }

                                if (v instanceof Function) {
                                    // if this is a workflow function, it'd return a Function object instead
                                    // of actually executing the function, so execute it in the CPS
                                    return ((Function)v).invoke(e, lhs, emptyList(),k);
                                } else {
                                    // if this was a normal property, we get the value as-is.
                                    return k.receive(v);
                                }
                            }
                        });
                    }
                });
            }
        };
    }

    public Block setProperty(Block lhs, String property, Block rhs) {
        return setProperty(lhs, constant(property), rhs);
    }

    public Block setProperty(final Block lhs, final Block property, final Block rhs) {
        return new Block() {
            public Next eval(final Env e, final Continuation k) {
                return lhs.eval(e,new Continuation() {
                    public Next receive(final Object lhs) {
                        return new Next(property,e,new Continuation() {
                            public Next receive(final Object property) {
                                return new Next(rhs,e,new Continuation() {
                                    public Next receive(Object rhs) {
                                        try {
                                            // TODO: verify the behaviour of Groovy if the property expression evaluates to non-String
                                            ScriptBytecodeAdapter.setProperty(rhs,
                                                    null/*Groovy doesn't use this parameter*/,
                                                    lhs, (String) property);
                                        } catch (Throwable t) {
                                            throw new UnsupportedOperationException(t);     // TODO: exception handling
                                        }
                                        return k.receive(null);
                                    }
                                });
                            }
                        });
                    }
                });
            }
        };
    }

    /**
     * Object instantiation.
     */
    public Block new_(final Class type, Block... argExps) {
        final CallSite callSite = fakeCallSite("<init>");
        final Block args = evalArgs(argExps);

        return new Block() {
            public Next eval(final Env e, final Continuation k) {
                return args.eval(e,new Continuation() {
                    public Next receive(Object _args) {
                        List args = (List) _args;

                        Object v;
                        try {
                            v = callSite.callConstructor(type, args.toArray(new Object[args.size()]));
                        } catch (Throwable t) {
                            throw new UnsupportedOperationException(t);     // TODO: exception handling
                        }

                        // constructor cannot be an asynchronous function
                        return k.receive(v);
                    }
                });
            }
        };
    }

    /**
     * Returns an expression that evaluates all the arguments and return it as a {@link List}.
     */
    private Block evalArgs(final Block... argExps) {
        if (argExps.length==0)  // no arguments to evaluate
            return new Constant(emptyList());

        return new Block() {
            public Next eval(final Env e, final Continuation k) {
                final List<Object> args = new ArrayList<Object>(argExps.length); // this is where we build up actual arguments

                Next n = null;
                for (int i = argExps.length - 1; i >= 0; i--) {
                    final Next nn = n;
                    n = new Next(argExps[i], e, new Continuation() {
                        public Next receive(Object o) {
                            args.add(o);
                            if (nn != null)
                                return nn;
                            else
                                return k.receive(args);
                        }
                    });
                }
                return n;
            }
        };
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
