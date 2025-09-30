package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.Binding;
import groovy.lang.GroovyObject;
import groovy.lang.Script;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class DelegatingScript extends Script {
    /**
     * Object to delegate the call to.
     */
    public GroovyObject o;

    public DelegatingScript() {}

    public DelegatingScript(Binding binding) {
        super(binding);
    }

    @Override
    public final Object invokeMethod(String name, Object args) {
        return o.invokeMethod(name, args);
    }
}
