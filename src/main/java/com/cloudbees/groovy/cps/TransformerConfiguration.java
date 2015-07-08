package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.CpsClosure;
import groovy.lang.Closure;
import org.codehaus.groovy.ast.ClassNode;

/**
 * Switches that affect the behaviour of {@link CpsTransformer}
 *
 * @author Kohsuke Kawaguchi
 * @see CpsTransformer#setConfiguration(TransformerConfiguration)
 */
public class TransformerConfiguration {
    private ClassNode closureType = new ClassNode(CpsClosure.class);

    public ClassNode getClosureType() {
        return closureType;
    }

    public TransformerConfiguration withClosureType(ClassNode closureType) {
        this.closureType = closureType;
        return this;
    }

    public TransformerConfiguration withClosureType(Class<? extends Closure> c) {
        return withClosureType(new ClassNode(c));
    }
}
