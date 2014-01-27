package com.cloudbees.groovy.cps;

import com.google.common.collect.ImmutableList;

import java.util.List;

/**
 * @author Kohsuke Kawaguchi
 */
public class Function {
    final Expression body;
    final ImmutableList<String> parameters;

    public Function(Expression body, List<String> parameters) {
        this.body = body;
        this.parameters = ImmutableList.copyOf(parameters);
    }

    public Next invoke(List<?> args, Continuation k) {
        EnvImpl e = new EnvImpl(null);
        assert args.size()== parameters.size();  // TODO: varargs

        for (int i=0; i< parameters.size(); i++) {
            e.set(parameters.get(i), args.get(i));
        }

        return new Next(body, e, k);
    }

}
