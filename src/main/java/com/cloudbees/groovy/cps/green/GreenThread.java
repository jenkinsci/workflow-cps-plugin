package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Continuable;

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
     * The type parameter and bogus return type helps us ensure that the resume value from the suspension
     * and the return type of the method matches.
     */
    private static <T> T invoke(ThreadTask<T> task) {
        Continuable.suspend(task);

        // the code will never reach here
        throw new AssertionError();
    }

    private GreenThreadState stateAt(GreenDispatcher d) {
        return d.resolveThreadState(this);
    }

    public boolean isAlive() {
        return invoke(new ThreadTask<Boolean>() {
            public Boolean eval(GreenDispatcher d) {
                return !stateAt(d).isDead();
            }
        });
    }

    public boolean isDead() {
        return invoke(new ThreadTask<Boolean>() {
            public Boolean eval(GreenDispatcher d) {
                return stateAt(d).isDead();
            }
        });
    }

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

    public static GreenThread currentThread() {
        return invoke(new ThreadTask<GreenThread>() {
            public GreenThread eval(GreenDispatcher d) throws Throwable {
                return d.currentThread().g;
            }
        });
    }
}
