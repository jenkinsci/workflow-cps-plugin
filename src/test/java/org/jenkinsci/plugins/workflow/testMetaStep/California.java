package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Kohsuke Kawaguchi
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
        hello.getLogger().println("California from "+ocean+" to "+mountain);
    }

    @Extension @Symbol("california")
    public static class DescriptorImpl extends Descriptor<State> {
    }
}
