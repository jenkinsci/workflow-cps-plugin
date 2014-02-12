package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.FunctionCallEnv;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class CpsClosureDef extends CpsCallable {
    public CpsClosureDef(List<String> parameters, Block body) {
        super(parameters, body);
    }

    @Override
    Next invoke(Env caller, Object receiver, List<?> args, Continuation k) {
        Env e = new FunctionCallEnv(caller, receiver, k);
        assert args.size()== parameters.size();  // TODO: varargs

        for (int i=0; i< parameters.size(); i++) {
            e.setLocalVariable(parameters.get(i), args.get(i));
        }

        return new Next(body, e, k);
    }
}
