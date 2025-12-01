package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTest;
import org.jenkinsci.plugins.workflow.testMetaStep.chemical.CarbonMonoxide;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * State whose name collides with another {@link Describable} that has its own meta-step
 *
 * @author Kohsuke Kawaguchi
 * @see CarbonMonoxide
 * @see SnippetizerTest#collisionWithAnotherMetaStep()
 */
public class Colorado extends State {
    @DataBoundConstructor
    public Colorado() {}

    @Override
    public void sayHello(TaskListener hello) {}

    @Extension
    @Symbol("CO")
    public static class DescriptorImpl extends Descriptor<State> {}
}
