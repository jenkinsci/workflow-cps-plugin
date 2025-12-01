package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.cps.DSLTest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Example of {@link Describable} that has one and only one required parameter.
 *
 * @author Kohsuke Kawaguchi
 * @see DSLTest#dollar_class_must_die_onearg()
 */
public class NewYork extends State {
    private final String motto;
    public boolean moderate;

    @DataBoundConstructor
    public NewYork(String motto) {
        this.motto = motto;
    }

    /**
     * Colliding with {@link StateMetaStep#moderate}
     * @see DSLTest#dollar_class_must_die_colliding_argument()
     */
    @DataBoundSetter
    public void setModerate(boolean m) {
        this.moderate = m;
    }

    @Override
    public void sayHello(TaskListener hello) {
        hello.getLogger().println("The " + motto + " State");
        if (moderate) hello.getLogger().println("New York can be moderate in spring or fall");
    }

    @Extension
    @Symbol("newYork")
    public static class DescriptorImpl extends Descriptor<State> {}
}
