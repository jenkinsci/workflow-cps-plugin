package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.impl.ConstantBlock;
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.ThrowBlock;
import groovy.lang.Closure;

/**
 * @author Kohsuke Kawaguchi
 */
public class GreenWorld {
    /**
     * Creates a new {@link Continuable} that supports green threads inside the code to be evaluated.
     */
    public static Continuable create(Block b) {
        GreenDispatcher d = new GreenDispatcher(0, new GreenThreadState(new GreenThread(), b));
        return new Continuable(d.asNext(null));
    }

    /**
     * Creates a new green thread that executes the given closure.
     */
    public static GreenThread startThread(Closure c) {
        Block b;
        try {
            Object r = c.call();

            // closure had run synchronously. Just create a sim
            b = new ConstantBlock(r);
        } catch (CpsCallableInvocation inv) {
            // this will create a thread, and resume with the newly created thread
            b = inv.asBlock();

        } catch (Throwable t) {
            // closure had run synchronously and failed
            b = new ThrowBlock(new ConstantBlock(t));
        }

        Continuable.suspend(new GreenThreadCreation(b));

        // thus the code will neve reach here
        throw new AssertionError();
    }

}
