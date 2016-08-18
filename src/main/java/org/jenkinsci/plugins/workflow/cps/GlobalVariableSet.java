package org.jenkinsci.plugins.workflow.cps;

import com.google.common.collect.Lists;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Util;
import hudson.model.Run;
import java.util.Collection;
import java.util.Iterator;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Extension point that defines a collection of global variables.
 * 
 * @author Kohsuke Kawaguchi
 * @see GlobalVariable#ALL
 */
public abstract class GlobalVariableSet implements ExtensionPoint, Iterable<GlobalVariable> {

    /**
     * Enumerate all global variables from this provider which should be associated with a given build.
     * @param run a build, which may or may not still be running; or may be left null to look for variables that exist without any context
     * @return a possibly empty set
     */
    public /* abstract */ @Nonnull Collection<GlobalVariable> forRun(@CheckForNull Run<?,?> run) {
        return Lists.newArrayList(iterator());
    }

    /** @deprecated implement {@link #forJob} instead */
    @Deprecated
    @Override public Iterator<GlobalVariable> iterator() {
        if (Util.isOverridden(GlobalVariableSet.class, getClass(), "forRun", Run.class)) {
            return forRun(null).iterator();
        } else {
            throw new AbstractMethodError(getClass().getName() + " must implement forRun");
        }
    }

    /**
     * Allow {@link GlobalVariable}s to be defined with {@link Extension}, and make them discoverable
     * via {@link GlobalVariableSet}. This simplifies the registration of single global variable.
     */
    @Extension
    @Restricted(NoExternalUse.class)
    public static class GlobalVariableProvider extends GlobalVariableSet {
        @Override
        public Collection<GlobalVariable> forRun(Run<?,?> run) {
            return ExtensionList.lookup(GlobalVariable.class);
        }
    }
}
