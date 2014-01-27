package com.cloudbees.groovy.cps;

import com.google.common.collect.ImmutableList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    public Next invoke(List<Object> args, Continuation k) {
        return new Next(body, new EnvImpl(null,args), k);
    }

    /**
     * New stack frame created when calling a function.
     */
    class EnvImpl implements Env {
        final Object _this;
        // TODO: delegate?
        final Map<String,Object> locals = new HashMap<String, Object>();

        EnvImpl(Object _this, List<Object> args) {
            this._this = _this;
            assert args.size()==parameters.size();  // TODO: varargs

            for (int i=0; i<parameters.size(); i++) {
                set(parameters.get(i),args.get(i));
            }
        }

        public Function resolveFunction(String name) {
            // TODO:
            return null;
        }

        public Object get(String name) {
            return locals.get(name);
        }

        public void set(String name, Object value) {
            locals.put(name,value);
        }

        public Env newBlockScope() {
            // TODO
            return this;
        }
    }
}
