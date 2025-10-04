package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.LValue;
import com.cloudbees.groovy.cps.LValueBlock;
import com.cloudbees.groovy.cps.Next;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * @author Kohsuke Kawaguchi
 */
public class StaticFieldBlock extends LValueBlock {
    private final Class lhs;
    private final String name;
    private final SourceLocation loc;

    public StaticFieldBlock(SourceLocation loc, Class lhs, String name) {
        this.lhs = lhs;
        this.name = name;
        this.loc = loc;
    }

    public Next evalLValue(final Env e, final Continuation k) {
        return k.receive(new LValueImpl(e));
    }

    class LValueImpl extends ContinuationGroup implements LValue {
        private final Env e;

        public LValueImpl(Env e) {
            this.e = e;
        }

        private Field resolve() {
            try {
                Field f = lhs.getField(name);
                if (Modifier.isStatic(f.getModifiers())) {
                    f.setAccessible(true);
                    return f;
                }
            } catch (NoSuchFieldException t) {
                // fall through
            }
            return null;
        }

        private Next throwNoSuchFieldError() {
            return throwException(e, new NoSuchFieldError(lhs.getName() + "." + name), loc, new ReferenceStackTrace());
        }

        public Next get(Continuation k) {
            try {
                Field r = resolve();
                if (r == null) return throwNoSuchFieldError();
                return k.receive(r.get(null));
            } catch (IllegalAccessException t) {
                return throwException(e, t, loc, new ReferenceStackTrace());
            }
        }

        public Next set(Object v, Continuation k) {
            try {
                Field r = resolve();
                if (r == null) return throwNoSuchFieldError();
                r.set(null, v);
                return k.receive(null);
            } catch (IllegalAccessException t) {
                return throwException(e, t, loc, new ReferenceStackTrace());
            }
        }

        private static final long serialVersionUID = 1L;
    }

    private static final long serialVersionUID = 1L;
}
