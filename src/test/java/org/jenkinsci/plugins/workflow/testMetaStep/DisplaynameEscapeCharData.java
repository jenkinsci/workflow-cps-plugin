package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.AbstractDescribableImpl;
import hudson.model.Descriptor;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Created by rishi.manidhar on 11/2/16.
 */
public class DisplaynameEscapeCharData extends AbstractDescribableImpl<DisplaynameEscapeCharData> {

    public final String firstArg;

    @DataBoundConstructor
    public DisplaynameEscapeCharData(String firstArg) {
           this.firstArg = firstArg;
    }

    public String getArgs() {
        return "First arg: " + firstArg;
    }

    @Extension
    public static class DescriptorImpl extends Descriptor<DisplaynameEscapeCharData> {
    }
}
