package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.impl.Outcome;
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import groovy.lang.Closure;

/**
 * @author Kohsuke Kawaguchi
 */
public class GreenWorld {
    /**
     * Creates a new {@link Continuable} that supports green threads inside the code to be evaluated.
     */
    public static Continuable create(Block b) {
        GreenDispatcher d = new GreenDispatcher(1, 0, new GreenThread(0,b));
        return new Continuable(d.asNext(null));
    }

    /**
     * Creates a new green thread that executes the given closure.
     */
    public static GreenThread startThread(Closure c) {
        try {
            Object r = c.call();

            // closure had run synchronously
            return new GreenThread(new Outcome(r,null));
        } catch (CpsCallableInvocation inv) {
            // this will create a thread, and resume with the newly created thread
            Continuable.suspend(new GreenThreadCreation(inv.asBlock()));

            // thus the code will neve reach here
            throw new AssertionError();
        } catch (Throwable t) {
            // closure had run synchronously and failed
            return new GreenThread(new Outcome(null,t));
        }
    }

}
