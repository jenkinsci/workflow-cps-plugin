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
    private final GreenThread[] t;
    private final int cur;
    private final Env e;

    public GreenDispatcher(int cur, GreenThread... t) {
        this.t = t;
        this.cur = cur;
        this.e = new ProxyEnv(currentThread().n.e);
    }

    GreenThread currentThread() {
        return t[cur];
    }

    Next update(GreenThread g) {
        GreenThread[] a;
        Outcome y = g.n.yield;

        if (y.getNormal() instanceof GreenThreadCreation) {
            GreenThreadCreation c = (GreenThreadCreation) y.getNormal();

            // create a new thread
            a = new GreenThread[t.length+1];
            System.arraycopy(t,0,a,0,cur);
            GreenThread nt = new GreenThread(c.block);
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
            a = new GreenThread[t.length-1];
            System.arraycopy(t,0,a,0,cur);
            System.arraycopy(t,cur+1,a,cur,t.length-cur);
            y = null; // green thread exiting will not yield a value
        } else {
            // replace the current slot
            a = new GreenThread[t.length];
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
            Next n = currentThread().n.k.receive(o);
            return update(new GreenThread(n));
        }
    };

    private final Block b = new Block() {
        public Next eval(Env e, Continuation k) {
            assert e==GreenDispatcher.this.e;
            assert k==GreenDispatcher.this.k;
            GreenThread t = currentThread();
            return t.n.f.eval(t.n.e, k);
        }
    };

    Next asNext(Outcome y) {
        if (y==null)    return new Next(b,e,k);
        else            return new Next(e,k,y);
    }
}
