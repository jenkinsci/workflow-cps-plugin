package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.sandbox.CallSiteTag;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Collection;
import java.util.Collections;

/**
 * Default implementation of {@link CallSiteBlock}.
 *
 * @author Kohsuke Kawaguchi
 */
public abstract class CallSiteBlockSupport implements CallSiteBlock {
    /**
     * To keep persisted form compact, we use null to represent empty set.
     * This is also convenient in case this object is deserialized from the old form.
     */
    private final Collection<CallSiteTag> tags;

    public CallSiteBlockSupport(Collection<CallSiteTag> tags) {
        if (tags.isEmpty()) tags = null;
        this.tags = tags;
    }

    @NonNull
    public Collection<CallSiteTag> getTags() {
        if (tags == null) return Collections.emptySet();
        return Collections.unmodifiableCollection(tags);
    }

    private static final long serialVersionUID = 1L;
}
