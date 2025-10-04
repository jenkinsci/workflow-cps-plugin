package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTest;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Recursive state
 *
 * @author Kohsuke Kawaguchi
 * @see SnippetizerTest#recursiveSymbolUse()
 */
public class Hawaii extends State {
    public Island island;

    @DataBoundConstructor
    public Hawaii(Island island) {
        this.island = island;
    }

    @Override
    public void sayHello(TaskListener hello) {}

    @Extension
    @Symbol("hawaii")
    public static class DescriptorImpl extends Descriptor<State> {}
}
