package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.model.AbstractDescribableImpl;
import hudson.model.TaskListener;
import org.jenkinsci.plugins.workflow.cps.DSLTest;

/**
 * @author Kohsuke Kawaguchi
 * @see DSLTest
 */
public abstract class State extends AbstractDescribableImpl<State> {
    public abstract void sayHello(TaskListener hello);
}
