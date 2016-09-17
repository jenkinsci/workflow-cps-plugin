package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import hudson.Extension;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Test step implemented in Groovy, which supports nested body block.
 *
 * @author Kohsuke Kawaguchi
 * @see GroovyStepTest#helloWorld()
 */
public class HelloWorldGroovyStep extends GroovyStep {
    private String message;

    @DataBoundConstructor
    public HelloWorldGroovyStep(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    @Extension
    public static class DescriptorImpl extends GroovyStepDescriptor {
        @Override
        public String getFunctionName() {
            return "helloWorldGroovy";
        }

        @Override
        public boolean takesImplicitBlockArgument() {
            return true;
        }
    }
}
