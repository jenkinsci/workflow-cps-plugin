package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import java.util.List;

/**
 * Gets a value from local variable and removes it.
 *
 * @author Kohsuke Kawaguchi
 * @see Continuable#suspend(Object)
 */
public class SuspendBlock implements Block {
    private SuspendBlock() {}

    public Next eval(Env e, final Continuation k) {
        Object v = e.getLocalVariable("suspendValue");
        e.setLocalVariable("suspendValue", null);

        return Next.yield(v, e, k);
    }

    private static final long serialVersionUID = 1L;

    /**
     * CPS Definition of the {@link Continuable#suspend(Object)} method.
     */
    public static final CpsFunction SUSPEND = new CpsFunction(List.of("suspendValue"), new SuspendBlock());
}
