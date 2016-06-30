package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.DSLTest;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Example of {@link Describable} that has one and only one required parameter.
 *
 * @author Kohsuke Kawaguchi
 * @see DSLTest#dollar_class_must_die_onearg()
 */
public class NewYork extends State {
    private final String motto;

    @DataBoundConstructor
    public NewYork(String motto) {
        this.motto = motto;
    }

    @Override
    public void sayHello(TaskListener hello) {
        hello.getLogger().println("The "+motto+" State");
    }

    @Extension
    @Symbol("newYork")
    public static class DescriptorImpl extends Descriptor<State> {
    }
}
