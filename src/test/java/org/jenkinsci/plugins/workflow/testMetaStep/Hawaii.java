package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTest;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * Recursive state
 *
 * @author Kohsuke Kawaguchi
 * @see SnippetizerTest#recursiveSymbolUse()
 */
public class Hawaii extends State {
    public Hawaii lhs;
    public Hawaii rhs;

    @DataBoundConstructor
    public Hawaii() {
    }

    public Hawaii(Hawaii lhs, Hawaii rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    @DataBoundSetter
    public void setLhs(Hawaii lhs) {
        this.lhs = lhs;
    }

    @DataBoundSetter
    public void setRhs(Hawaii rhs) {
        this.rhs = rhs;
    }

    @Override
    public void sayHello(TaskListener hello) {
    }

    @Extension
    @Symbol("hawaii")
    public static class DescriptorImpl extends Descriptor<State> {
    }
}
