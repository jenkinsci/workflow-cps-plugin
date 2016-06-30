package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.model.AbstractDescribableImpl;
import hudson.model.TaskListener;

/**
 * @author Kohsuke Kawaguchi
 */
public abstract class State extends AbstractDescribableImpl<State> {
    public abstract void sayHello(TaskListener hello);
}
