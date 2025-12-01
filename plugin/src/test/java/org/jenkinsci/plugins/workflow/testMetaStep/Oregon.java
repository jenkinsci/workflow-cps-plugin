package org.jenkinsci.plugins.workflow.testMetaStep;

import hudson.Extension;
import hudson.model.Descriptor;
import hudson.model.TaskListener;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.workflow.cps.SnippetizerTest;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * State whose name collides with a step.
 *
 * @author Kohsuke Kawaguchi
 * @see OrStep
 * @see SnippetizerTest#collisionWithStep()
 */
public class Oregon extends State {

    @DataBoundConstructor
    public Oregon() {}

    @Override
    public void sayHello(TaskListener hello) {
        hello.getLogger().println("Alis volat propriis");
    }

    @Extension
    @Symbol("or")
    public static class DescriptorImpl extends Descriptor<State> {}
}
