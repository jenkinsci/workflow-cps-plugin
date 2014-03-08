package com.cloudbees.groovy.cps.green;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import com.cloudbees.groovy.cps.impl.Outcome;
import com.cloudbees.groovy.cps.impl.ProxyEnv;

import java.io.Serializable;

/**
 *
 * The whole thing has to be immutable because cloning {@link Continuable} is just shallow-copying its variables.
 *
 * @author Kohsuke Kawaguchi
 */
class GreenDispatcher implements Serializable {
    final GreenThreadState[] threads;
    private final int cur;
    private final Env e;

    public GreenDispatcher(int cur, GreenThreadState... threads) {
        this.threads = threads;
        this.cur = cur;
        this.e = new ProxyEnv(currentThread().n.e);
    }

    GreenThreadState currentThread() {
        return threads[cur];
    }

    GreenDispatcher withNewThread(GreenThreadState s) {
        GreenThreadState[] a = new GreenThreadState[threads.length+1];
        System.arraycopy(threads,0,a,0, threads.length);
        a[threads.length] = s;
        return new GreenDispatcher(cur,a);
    }

    /**
     * Updates the thread state. If the thread is dead, it'll be removed.
     */
    GreenDispatcher with(GreenThreadState s) {
        int idx = -1;
        for (int i = 0; i < threads.length; i++) {
            if (threads[i].g==s.g) {
                threads[i] = s;
                idx = i;
                break;
            }
        }
        if (idx==-1)
            throw new IllegalStateException("No such thread: "+s.g);

        GreenDispatcher d;
        if (s.isDead()) {
            GreenThreadState[] a = new GreenThreadState[threads.length-1];
            System.arraycopy(threads,0,a,0,idx);
            System.arraycopy(threads,idx+1,a,cur, threads.length-idx);

            d = new GreenDispatcher(idx<cur?cur-1:cur, a);
        } else {
            GreenThreadState[] a = new GreenThreadState[threads.length];
            System.arraycopy(threads,0,a,0, threads.length);
            a[idx] = s;

            int cur = this.cur;

            d = new GreenDispatcher(cur,a);
        }

        // if the current green thread isn't runnable, need to pick up a runnable green thread
        for (int i=0; true; i++, d=d.withNewCur()) {
            if (d.currentThread().isRunnable())
                return d;
            if (i==d.threads.length)
                throw new IllegalStateException("No threads are runnable. Deadlock?");  // TODO: diagnose the lock
        }
    }

    /**
     * Switch to the next runnable thread.
     */
    GreenDispatcher withNewCur() {
        int cur = this.cur+1;

        for (int cnt=0; cnt<threads.length; cnt++) {
            int i = cur % threads.length;
            if (threads[i].isRunnable())
                return new GreenDispatcher(i,threads);
        }
        throw new IllegalStateException("No threads are runnable. Deadlock?");  // TODO: diagnose the lock
    }

    /**
     * Called when we execute something in one of the member thread.
     *
     * We'll build an updated {@link GreenDispatcher} then return it.
     */
    Next update(GreenThreadState g) {
        GreenDispatcher d = this.with(g);
        Outcome y = g.n.yield;

        if (y==null) {
            // no yield. rotate to next thread and keep going
            return d.withNewCur().asNext(null);
        }

        if (y.getNormal() instanceof ThreadTask) {
            // a task that needs to update/access the state
            ThreadTask task = (ThreadTask)y.getNormal();

            Result r = task.eval(d);
            d = r.d;
            if (r.suspend)  // yield the value, then come back to the current thread later
                return d.asNext(r.value);
            else
                return d.update(g.resumeFrom(r.value));
        } else {
            // other Outcome is for caller
            return d.asNext(y);
        }
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
        for (GreenThreadState ts : threads)
            if (ts.g==g)
                return ts;
        throw new IllegalStateException("Invalid green thread: "+g);
    }

    private static final long serialVersionUID = 1L;
}
