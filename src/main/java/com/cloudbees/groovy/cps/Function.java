package com.cloudbees.groovy.cps;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class Function {
    final Expression body;
    final ImmutableList<String> parameters;

    public Function(List<String> parameters, Expression body) {
        this.body = body;
        this.parameters = ImmutableList.copyOf(parameters);
    }

    public Next invoke(List<?> args, Continuation k) {
        Env e = new FunctionCallEnv(null,k);
        assert args.size()== parameters.size();  // TODO: varargs

        for (int i=0; i< parameters.size(); i++) {
            e.setLocalVariable(parameters.get(i), args.get(i));
        }

        return new Next(body, e, k);
    }

}
