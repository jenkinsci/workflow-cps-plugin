package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.impl.Outcome;

/**
 * Represents a green thread.
 *
 * @author Kohsuke Kawaguchi
 */
public class GreenThread {
    GreenThread() {
    }

    /**
     * Executes the task and make its result available back to the caller.
     *
     * Bogus return type is just for a convenience
     */
    private static void invoke(ThreadTask task) {
        Continuable.suspend(task);

        // the code will never reach here
        throw new AssertionError();
    }

    private GreenThreadState stateAt(GreenDispatcher d) {
        return d.resolveThreadState(this);
    }

    public boolean isAlive() {
        invoke(new ThreadTask() {
            public Result eval(GreenDispatcher d) {
                return new Result(d, new Outcome(!stateAt(d).isDead(),null), false);
            }
        });
        throw new AssertionError();
    }

//    public boolean isDead() {
//        return invoke(new ThreadTask<Boolean>() {
//            public Boolean eval(GreenDispatcher d) {
//                return stateAt(d).isDead();
//            }
//        });
//    }

//    // TODO: this is not very useful because it doesn't block for the completion
//    public Object getResult() throws InvocationTargetException {
//        Continuable.suspend(new ThreadTask() {
//            public Object eval(GreenDispatcher d) throws Throwable {
//                return d.resolveThreadState(id).getResult().replay();
//            }
//        });
//
//        // the code will never reach here
//        throw new AssertionError();
//    }

//    public static GreenThread currentThread() {
//        return invoke(new ThreadTask<GreenThread>() {
//            public GreenThread eval(GreenDispatcher d) throws Throwable {
//                return d.currentThread().g;
//            }
//        });
//    }

    public static void monitorEnter(final Object o) {
        invoke(new ThreadTask<Void>() {
            public Void eval(GreenDispatcher d) throws Throwable {
                d.monitorEnter(o);
                return null;
            }
        });
    }
}
