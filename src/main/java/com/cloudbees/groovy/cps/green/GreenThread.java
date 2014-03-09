package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.impl.ConstantBlock;
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.Outcome;
import com.cloudbees.groovy.cps.impl.ThrowBlock;

/**
 * Represents a green thread.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class GreenThread implements Runnable {
    public GreenThread() {
    }

    /**
     * Creates a new green thread that executes the given closure.
     */
    public void start() {
        Block b;
        try {
            run();

            // closure had run synchronously.
            b = Block.NOOP;
        } catch (CpsCallableInvocation inv) {
            // this will create a thread, and resume with the newly created thread
            b = inv.asBlock();
        } catch (Throwable t) {
            // closure had run synchronously and failed
            b = new ThrowBlock(new ConstantBlock(t));
        }

        final Block bb = b;
        invoke(new ThreadTask() {
            public Result eval(GreenWorld d) {
                d = d.withNewThread(new GreenThreadState(GreenThread.this,bb));
                return new Result(d, new Outcome(GreenThread.this,null), false);
            }
        });

        // thus the code will never reach here
        throw new AssertionError();
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

    private GreenThreadState stateAt(GreenWorld d) {
        return d.resolveThreadState(this);
    }

    public boolean isAlive() {
        invoke(new ThreadTask() {
            public Result eval(GreenWorld d) {
                return new Result(d, new Outcome(!stateAt(d).isDead(),null), false);
            }
        });
        throw new AssertionError();
    }

//    public boolean isDead() {
//        return invoke(new ThreadTask<Boolean>() {
//            public Boolean eval(GreenWorld d) {
//                return stateAt(d).isDead();
//            }
//        });
//    }

//    // TODO: this is not very useful because it doesn't block for the completion
//    public Object getResult() throws InvocationTargetException {
//        Continuable.suspend(new ThreadTask() {
//            public Object eval(GreenWorld d) throws Throwable {
//                return d.resolveThreadState(id).getResult().replay();
//            }
//        });
//
//        // the code will never reach here
//        throw new AssertionError();
//    }

    public static GreenThread currentThread() {
        invoke(new ThreadTask() {
            public Result eval(GreenWorld d) {
                return new Result(d, new Outcome(d.currentThread().g,null), false);
            }
        });
        throw new AssertionError();
    }

    public static void monitorEnter(final Object o) {
        invoke(new ThreadTask() {
            public Result eval(GreenWorld d) {
                return new Result(trans(d),null,false);
            }
            public GreenWorld trans(GreenWorld d) {
                GreenThreadState cur = d.currentThread();
                for (GreenThreadState t : d.threads) {
                    if (t!=cur && t.hasMonitor(o)) {
                        // someone else has lock, so we need to wait
                        return d.with(cur.withCond(Cond.MONITOR_ENTER, o));
                    }
                }
                // no one else has a lock, so we acquire the lock and move on
                return d.with(cur.pushMonitor(o));
            }
        });
        throw new AssertionError();
    }

    public static void monitorLeave() {
        invoke(new ThreadTask() {
            public Result eval(GreenWorld d) {
                GreenThreadState cur = d.currentThread();
                final Object o = cur.monitor.o;

                // the current thread will release the monitor.
                d = d.with(cur.popMonitor());

                // if another thread is waiting for this monitor, he gets one right away
                for (GreenThreadState t : d.threads) {
                    if (t.cond==Cond.MONITOR_ENTER && t.wait==o) {
                        // give the lock to this thread
                        d = d.with(t.withCond(null,null).pushMonitor(o));
                        break;
                    }
                    if (t.cond==Cond.NOTIFIED && t.wait==o) {
                        // give the lock to this thread (but without new monitor)
                        d = d.with(t.withCond(null,null));
                        break;
                    }
                }

                return new Result(d,null,false);
            }
        });
        throw new AssertionError();
    }

    public static void wait(final Object o) {
        invoke(new ThreadTask() {
            public Result eval(GreenWorld d) {
                GreenThreadState cur = d.currentThread();

                if (!cur.hasMonitor(o))
                    throw new IllegalStateException("Thread doesn't have a lock of "+o);

                // wait for the notification to arrive
                d = d.with(cur.withCond(Cond.WAIT, o));

                return new Result(d,null,false);
            }
        });
        throw new AssertionError();
    }

    public static void notify(final Object o, final boolean all) {
        invoke(new ThreadTask() {
            public Result eval(GreenWorld d) {
                GreenThreadState cur = d.currentThread();

                if (!cur.hasMonitor(o))
                    throw new IllegalStateException("Thread doesn't have a lock of "+o);

                // let other waiting threads come back to life
                for (GreenThreadState t : d.threads) {
                    if (t.wait==o) {
                        d = d.with(t.withCond(Cond.NOTIFIED, o));
                        if (!all)
                            break;
                    }
                }

                return new Result(d,null,false);
            }
        });
        throw new AssertionError();
    }
}
