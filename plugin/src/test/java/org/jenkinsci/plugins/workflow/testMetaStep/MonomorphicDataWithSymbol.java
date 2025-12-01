package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

public class MonomorphicDataWithSymbol extends AbstractDescribableImpl<MonomorphicDataWithSymbol> {
    public final String firstArg;

    public final String secondArg;

    @DataBoundConstructor
    public MonomorphicDataWithSymbol(String firstArg, String secondArg) {
        this.firstArg = firstArg;
        this.secondArg = secondArg;
    }

    public String getArgs() {
        return "First arg: " + firstArg + ", second arg: " + secondArg;
    }

    @Extension
    @Symbol("monomorphSymbol")
    public static class DescriptorImpl extends Descriptor<MonomorphicDataWithSymbol> {}
}
