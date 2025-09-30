package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

public class MonomorphicData extends AbstractDescribableImpl<MonomorphicData> {
    public final String firstArg;

    public final String secondArg;

    @DataBoundConstructor
    public MonomorphicData(String firstArg, String secondArg) {
        this.firstArg = firstArg;
        this.secondArg = secondArg;
    }

    public String getArgs() {
        return "First arg: " + firstArg + ", second arg: " + secondArg;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<MonomorphicData> {}
}
