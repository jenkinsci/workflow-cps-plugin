package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.cps.DSLTest;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Example of zero-arg Describable
 *
 * @author Kohsuke Kawaguchi
 * @see DSLTest#dollar_class_must_die3()
 */
public class Nevada extends State {

    @DataBoundConstructor
    public Nevada() {}

    @Override
    public void sayHello(TaskListener hello) {
        hello.getLogger().println("All For Our Country");
    }

    @Extension
    @Symbol("nevada")
    public static class DescriptorImpl extends Descriptor<State> {}
}
