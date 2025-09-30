package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import com.cloudbees.groovy.cps.Outcome;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.Whitelisted;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Safepoint called from the CPS interpreter.
 *
 * @author Kohsuke Kawaguchi
 */
// classes compiled from Pipeline Script refers to this class, so it needs to be public, but not meant for external
// consumption
@Restricted(NoExternalUse.class)
public class Safepoint extends ThreadTask {
    /**
     * Method called from CPS interpreter.
     *
     * Suspend the execution to yield CPS VM thread to other activities.
     */
    @Whitelisted
    public static void safepoint() {
        Continuable.suspend("safepoint", new Safepoint());
    }

    @Override
    protected ThreadTaskResult eval(CpsThread cur) {
        // immediately resume with null value, but this will happen in another
        // invocation of CpsThread.runNextChunk() after yielding.
        return ThreadTaskResult.resumeWith(new Outcome(null, null));
    }
}
