package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Recursive islands
 * @author Kohsuke Kawaguchi
 * @see Hawa
 *
 */
public class Island extends AbstractDescribableImpl<Island> {
    public Island lhs;
    public Island rhs;

    @DataBoundConstructor
    public Island() {
    }

    public Island(Island lhs, Island rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @DataBoundSetter
    public void setLhs(Island lhs) {
        this.lhs = lhs;
    }

    @DataBoundSetter
    public void setRhs(Island rhs) {
        this.rhs = rhs;
    }

    @Extension
    @Symbol("island")
    public static class DescriptorImpl extends Descriptor<Island> {
    }
}
