package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.cps.DSLTest;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Example of {@link Describable} with lots of arguments.
 *
 * @author Kohsuke Kawaguchi
 * @see DSLTest#dollar_class_must_die()
 */
public class California extends State {
    private final String ocean;
    private final String mountain;

    @DataBoundConstructor
    public California(String ocean, String mountain) {
        this.ocean = ocean;
        this.mountain = mountain;
    }

    @Override
    public void sayHello(TaskListener hello) {
        hello.getLogger().println("California from " + ocean + " to " + mountain);
    }

    @Extension
    @Symbol("california")
    public static class DescriptorImpl extends Descriptor<State> {}
}
