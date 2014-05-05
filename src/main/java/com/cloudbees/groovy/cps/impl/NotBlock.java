package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

/**
 * !b
 *
 * @author Kohsuke Kawaguchi
 */
public class NotBlock implements Block {
    private final Block b;

    public NotBlock(Block b) {
        this.b = b;
    }

    public Next eval(Env e, final Continuation k) {
        return b.eval(e,new Continuation() {
            public Next receive(Object o) {
                boolean b = DefaultTypeTransformation.booleanUnbox(o);
                return k.receive(!b);
            }
        });
    }
}
