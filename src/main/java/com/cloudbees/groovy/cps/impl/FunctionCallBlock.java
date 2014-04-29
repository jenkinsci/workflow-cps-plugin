package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;

/**
 * lhs.name(arg,arg,...)
 *
 * @author Kohsuke Kawaguchi
 */
public class FunctionCallBlock implements Block {
    /**
     * Receiver of the call
     */
    private final Block lhsExp;

    /**
     * Method name.
     * "&lt;init>" to call constructor
     */
    private final Block nameExp;

    /**
     * Arguments to the call.
     */
    private final Block[] argExps;

    private final SourceLocation loc;

    public FunctionCallBlock(SourceLocation loc, Block lhsExp, Block nameExp, Block[] argExps) {
        this.loc = loc;
        this.lhsExp = lhsExp;
        this.nameExp = nameExp;
        this.argExps = argExps;
    }

    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e,k).then(lhsExp,e,fixLhs);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation k;
        final Env e;

        Object lhs;
        String name;
        Object[] args = new Object[argExps.length];
        int idx;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next fixLhs(Object lhs) {
            this.lhs = lhs;
            return then(nameExp,e,fixName);
        }

        public Next fixName(Object name) {
            this.name = name.toString();    // TODO: verify the semantics if the value resolves to something other than String
            return dispatchOrArg();
        }

        public Next fixArg(Object v) {
            args[idx++] = v;
            return dispatchOrArg();
        }

        /**
         * If there are more arguments to evaluate, do so. Otherwise evaluate the function.
         */
        private Next dispatchOrArg() {
            if (args.length>idx)
                return then(argExps[idx],e,fixArg);
            else {
                if (name.equals("<init>")) {
                    // constructor call
                    Object v;
                    try {
                        v = fakeCallSite("<init>").callConstructor(lhs,args);
                    } catch (Throwable t) {
                        return throwException(e, t);
                    }
                    return k.receive(v);
                } else {
                    // regular method call
                    return methodCall(e,loc,k,lhs,name,args);
                }
            }
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr fixLhs = new ContinuationPtr(ContinuationImpl.class,"fixLhs");
    static final ContinuationPtr fixName = new ContinuationPtr(ContinuationImpl.class,"fixName");
    static final ContinuationPtr fixArg = new ContinuationPtr(ContinuationImpl.class,"fixArg");

    private static final long serialVersionUID = 1L;
}
