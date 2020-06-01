package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.LValue;
import com.cloudbees.groovy.cps.LValueBlock;
import com.cloudbees.groovy.cps.Next;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;

/**
 * Access to local variables and method parameters.
 *
 * @author Kohsuke Kawaguchi
 */
public class LocalVariableBlock extends LValueBlock {
    private final String name;
    private SourceLocation loc;

    public LocalVariableBlock(SourceLocation loc, String name) {
        this.name = name;
        this.loc = loc;
    }

    public LocalVariableBlock(String name) {
        this(null, name);
    }

    public Next evalLValue(final Env e, Continuation k) {
        return k.receive(new LocalVariable(e));
    }

    class LocalVariable extends ContinuationGroup implements LValue {
        private final Env e;

        LocalVariable(Env e) {
            this.e = e;
        }

        public Next get(Continuation k) {
            return k.receive(e.getLocalVariable(name));
        }

        public Next set(Object v, Continuation k) {
            Class type = e.getLocalVariableType(name);
            try {
                e.setLocalVariable(name, (type == null) ? v : ScriptBytecodeAdapter.castToType(v, type));
            } catch (Throwable t) {
                return throwException(e, t, loc, new ReferenceStackTrace());
            }

            return k.receive(null);
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
