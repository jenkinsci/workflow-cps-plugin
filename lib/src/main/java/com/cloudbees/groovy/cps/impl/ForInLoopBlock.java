package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import java.util.Iterator;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;

/**
 * for (Type var in col) { ... }
 *
 * @author Kohsuke Kawaguchi
 */
public class ForInLoopBlock implements Block {
    final String label;
    final Class type;
    final String variable;
    final Block collection;
    final Block body;
    final SourceLocation loc;

    public ForInLoopBlock(SourceLocation loc, String label, Class type, String variable, Block collection, Block body) {
        this.loc = loc;
        this.label = label;
        this.type = type;
        this.variable = variable;
        this.collection = collection;
        this.body = body;
    }

    public Next eval(Env e, Continuation k) {
        ContinuationImpl c = new ContinuationImpl(e, k);
        return c.then(collection, e, loopHead);
    }

    class ContinuationImpl extends ContinuationGroup {
        final Continuation loopEnd;
        final Env e;

        Iterator itr;

        ContinuationImpl(Env _e, Continuation loopEnd) {
            this.e = new LoopBlockScopeEnv(_e, label, loopEnd, increment.bind(this), 1);
            this.e.declareVariable(type, variable);
            this.loopEnd = loopEnd;
        }

        public Next loopHead(Object col) {
            try {
                itr = (Iterator) ScriptBytecodeAdapter.invokeMethod0(null /*unused*/, col, "iterator");
            } catch (Throwable t) {
                return throwException(e, t, loc, new ReferenceStackTrace());
            }

            return increment(null);
        }

        public Next increment(Object unused) {
            if (itr.hasNext()) {
                // one more iteration
                e.setLocalVariable(variable, itr.next());
                return then(body, e, increment);
            } else {
                // exit loop
                return loopEnd.receive(null);
            }
        }

        private static final long serialVersionUID = 1L;
    }

    static final ContinuationPtr loopHead = new ContinuationPtr(ContinuationImpl.class, "loopHead");
    static final ContinuationPtr increment = new ContinuationPtr(ContinuationImpl.class, "increment");

    private static final long serialVersionUID = 1L;
}
