package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.impl.Outcome;
import com.cloudbees.groovy.cps.impl.ProxyEnv;

/**
 *
 * The whole thing has to be immutable because cloning {@link Continuable} is just shallow-copying its variables.
 *
 * @author Kohsuke Kawaguchi
 */
class GreenDispatcher {
    private final GreenThreadState[] t;
    private final int cur;
    private final Env e;

    public GreenDispatcher(int cur, GreenThreadState... t) {
        this.t = t;
        this.cur = cur;
        this.e = new ProxyEnv(currentThread().n.e);
    }

    GreenThreadState currentThread() {
        return t[cur];
    }

    /**
     * Called when we execute something in one of the member thread.
     *
     * We'll build an updated {@link GreenDispatcher} then return it.
     */
    Next update(GreenThreadState g) {
        GreenThreadState[] a;
        Outcome y = g.n.yield;

        if (y.getNormal() instanceof ThreadTask) {
            // execute the task and get it right back to the thread
            ThreadTask task = (ThreadTask)y.getNormal();

            try {
                y = new Outcome(task.eval(this),null);
            } catch (Throwable t) {
                y = new Outcome(null,t);
            }

            // get back to the calling thread right away with the result
            return update(g.resumeFrom(y));
        }

        if (y.getNormal() instanceof GreenThreadCreation) {
            GreenThreadCreation c = (GreenThreadCreation) y.getNormal();

            // create a new thread
            a = new GreenThreadState[t.length+1];
            System.arraycopy(t,0,a,0,t.length);
            GreenThreadState nt = new GreenThreadState(new GreenThread(),c.block);
            a[t.length] = nt;

            // let the creator thread receive the newly created thread
            GreenDispatcher d = new GreenDispatcher(cur,a);
            return d.k.receive(nt);
        }

        if (g.isDead()) {// the thread has died
            if (t.length==1) {
                // if all the thread has terminated, we are done.
                return Next.terminate(y);
            }

            // remove this thread
            a = new GreenThreadState[t.length-1];
            System.arraycopy(t,0,a,0,cur);
            System.arraycopy(t,cur+1,a,cur,t.length-cur);
            y = null; // green thread exiting will not yield a value
        } else {
            // replace the current slot
            a = new GreenThreadState[t.length];
            System.arraycopy(t,0,a,0,t.length);
            a[cur] = g;
        }

        // pick the next thread to run.
        // if the current thread has yielded a value, we want to suspend with that and when the response comes back
        // we want to deliver that to the same thread, so we need to pick the current thread
        // otherwise schedule the next one
        GreenDispatcher d = new GreenDispatcher((y!=null ? cur + 1 : cur) % a.length, a);
        return d.asNext(y);
    }

    private final Continuation k = new Continuation() {
        public Next receive(Object o) {
            return update(currentThread().tick(o));
        }
    };

    private final Block b = new Block() {
        public Next eval(Env e, Continuation k) {
            return update(currentThread().step());
        }
    };

    Next asNext(Outcome y) {
        if (y==null)    return new Next(b,e,k);
        else            return new Next(e,k,y);
    }

    public GreenThreadState resolveThreadState(GreenThread g) {
        for (GreenThreadState ts : t)
            if (ts.g==g)
                return ts;
        throw new IllegalStateException("Invalid green thread: "+g);
    }
}
