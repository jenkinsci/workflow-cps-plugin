package com.cloudbees.groovy.cps.sandbox;

import com.cloudbees.groovy.cps.Builder;
import com.cloudbees.groovy.cps.CpsTransformer;
import com.cloudbees.groovy.cps.impl.CallSiteBlock;
import java.io.Serializable;

/**
 * Marker interface for objects that add metadata to call sites.
 *
 * <p>
 * This can be used by {@link CpsTransformer}s to influence how the code
 * is executed, for example by allowing code to run inside sandbox.
 *
 * @author Kohsuke Kawaguchi
 * @see CallSiteBlock
 * @see Builder#contextualize(CallSiteTag...)
 */
public interface CallSiteTag extends Serializable {}
