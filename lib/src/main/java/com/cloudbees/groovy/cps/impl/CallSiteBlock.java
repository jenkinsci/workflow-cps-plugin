package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.sandbox.CallSiteTag;
import com.cloudbees.groovy.cps.sandbox.Invoker;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.Serializable;
import java.util.Collection;

/**
 * {@link Block} that has {@link CallSiteTag}s.
 *
 * @author Kohsuke Kawaguchi
 * @see Invoker#contextualize(CallSiteBlock)
 */
public interface CallSiteBlock extends Serializable, Block {
    /**
     * Tags associated with this call site.
     */
    @NonNull
    Collection<CallSiteTag> getTags();
}
