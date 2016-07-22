package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.sandbox.CallSiteTag;
import com.cloudbees.groovy.cps.sandbox.Invoker;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.Collection;

/**
 * {@link Block}s that has {@link CallSiteTag}s.
 *
 * @author Kohsuke Kawaguchi
 * @see Invoker#contextualize(CallSiteBlock)
 */
public interface CallSiteBlock extends Serializable, Block {
    /**
     * Tags assocaited with this call site.
     */
    @Nonnull Collection<CallSiteTag> getTags();
}
