package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.Constant;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class Builder {
    public Expression constant(Object o) {
        return new Constant(o);
    }

    public Expression sequence(final Expression exp1, final Expression exp2) {
        return new Expression() {
            public Next eval(Env e, final Continuation k) {
                return new Next(exp1,e,new Continuation() {
                    public Next receive(Env e, Object _) {
                        return new Next(exp2,e,k);
                    }
                });
            }
        };
    }

    public Expression getLocalVariable(final String name) {
        return new Expression() {
            public Next eval(Env e, Continuation k) {
                return k.receive(e, e.get(name));
            }
        };
    }

    public Expression setLocalVariable(final String name, final Expression rhs) {
        return new Expression() {
            public Next eval(final Env e, final Continuation k) {
                return rhs.eval(e,new Continuation() {
                    public Next receive(Env _, Object o) {
                        e.set(name,o);
                        return k.receive(_,o);
                    }
                });
            }
        };
    }

    public Expression _if(final Expression cond, final Expression then, final Expression els) {
        return new Expression() {
            public Next eval(final Env e, final Continuation k) {
                return cond.eval(e,new Continuation() {
                    public Next receive(Env _, Object o) {
                        return (asBoolean(o) ? then : els).eval(e,k);
                    }
                });
            }
        };
    }

    public Expression functionCall(final String name, final Expression[] argExps) {
        return new Expression() {
            public Next eval(Env e, final Continuation k) {
                final List<Object> args = new ArrayList<Object>(argExps.length); // this is where we build up actual arguments

                final Function f = e.resolveFunction(name);   // body of the function

                Next n = null;
                for (int i=argExps.length-1; i>=0; i--) {
                    final Next nn = n;
                    n = new Next(argExps[i], e, new Continuation() {
                        public Next receive(Env e, Object o) {
                            args.add(o);
                            if (nn!=null)
                                return nn;
                            else
                                return f.invoke(args,k);
                        }
                    });
                }
                return n;
            }
        };
    }

}
