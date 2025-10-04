package org.jenkinsci.plugins.workflow.testMetaStep.chemical;

import hudson.Extension;
import hudson.model.Describable;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTest;
import org.jenkinsci.plugins.workflow.testMetaStep.Colorado;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * State whose name collides with another {@link Describable} that has its own meta-step
 *
 * @author Kohsuke Kawaguchi
 * @see Colorado
 * @see SnippetizerTest#collisionWithAnotherMetaStep()
 */
public class CarbonMonoxide extends Chemical {
    @DataBoundConstructor
    public CarbonMonoxide() {}

    @Extension
    @Symbol("CO")
    public static class DescriptorImpl extends Descriptor<Chemical> {}
}
