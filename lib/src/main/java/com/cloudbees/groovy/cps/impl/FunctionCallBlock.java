package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.sandbox.CallSiteTag;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static com.cloudbees.groovy.cps.impl.SourceLocation.UNKNOWN;

/**
 * lhs.name(arg,arg,...)
 *
 * @author Kohsuke Kawaguchi
 */
public class FunctionCallBlock extends CallSiteBlockSupport {
    /**
     * Receiver of the call
     */
    private final Block lhsExp;

    /**
     * Method name.
     * {@code "<init>"} to call constructor
     */
    private final Block nameExp;

    /**
     * Arguments to the call.
     */
    private final Block[] argExps;

    private final SourceLocation loc;

    private final boolean safe;

    public FunctionCallBlock(SourceLocation loc, Collection<CallSiteTag> tags, Block lhsExp, Block nameExp, boolean safe, Block[] argExps) {
        super(tags);
        this.loc = loc;
        this.lhsExp = lhsExp;
        this.nameExp = nameExp;
        this.safe = safe;
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
                Object[] expandedArgs = SpreadBlock.despreadList(args);
                if (name.equals("<init>")) {
                    // constructor call
                    Object v;
                    try {
                        v = e.getInvoker().contextualize(FunctionCallBlock.this).constructorCall((Class)lhs, expandedArgs);
                    } catch (Throwable t) {
                        if (t instanceof CpsCallableInvocation) {
                            ((CpsCallableInvocation) t).checkMismatch(lhs, List.of(name));
                        }
                        return throwException(e, t, loc, new ReferenceStackTrace());
                    }
                    if (v instanceof Throwable)
                        fillInStackTrace(e,(Throwable)v);

                    return k.receive(v);
                } else {
                    // regular method call
                    if (safe && lhs == null) {
                        return k.receive(null);
                    } else {
                        return methodCall(e, loc, k, FunctionCallBlock.this, lhs, name, expandedArgs);
                    }
                }
            }
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Insert the logical CPS stack trace in front of the actual stack trace.
     */
    private void fillInStackTrace(Env e, Throwable t) {
        List<StackTraceElement> stack = new ArrayList<>();
        stack.add((loc!=null ? loc : UNKNOWN).toStackTrace());
        e.buildStackTraceElements(stack,Integer.MAX_VALUE);
        stack.add(Continuable.SEPARATOR_STACK_ELEMENT);
        stack.addAll(List.of(t.getStackTrace()));
        t.setStackTrace(stack.toArray(new StackTraceElement[stack.size()]));
    }

    static final ContinuationPtr fixLhs = new ContinuationPtr(ContinuationImpl.class,"fixLhs");
    static final ContinuationPtr fixName = new ContinuationPtr(ContinuationImpl.class,"fixName");
    static final ContinuationPtr fixArg = new ContinuationPtr(ContinuationImpl.class,"fixArg");

    private static final long serialVersionUID = 1L;
}
