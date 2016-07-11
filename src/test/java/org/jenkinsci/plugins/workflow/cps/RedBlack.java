package org.jenkinsci.plugins.workflow.cps;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * @author Kohsuke Kawaguchi
 */
public class RedBlack extends AbstractDescribableImpl<RedBlack> {
    public RedBlack red, black;

    @DataBoundConstructor
    public RedBlack() {
    }

    @DataBoundSetter
    public void setRed(RedBlack v) {
        this.red = v;
    }

    @DataBoundSetter
    public void setBlack(RedBlack v) {
        this.black = v;
    }

    @Extension
    @Symbol("rb")
    public static class DescriptorImpl extends Descriptor<RedBlack> {

    }
}
