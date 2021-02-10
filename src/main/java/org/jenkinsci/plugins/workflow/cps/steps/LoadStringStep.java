package org.jenkinsci.plugins.workflow.cps.steps;

import hudson.Extension;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * Evaluate arbitrary string
 *
 * @author Kohsuke Kawaguchi
 */
public class LoadStringStep extends AbstractStepImpl {
    /**
     * Content of the script to be executed
     */
    private final String content;

    @DataBoundConstructor
    public LoadStringStep(String content) {
        this.content = content;
    }
    
    public String getContent() {
        return content;
    }

    @Extension
    public static class DescriptorImpl extends AbstractStepDescriptorImpl {
        public DescriptorImpl() {
            super(LoadStringStepExecution.class);
        }

        @Override
        public String getFunctionName() {
            return "loadString";
        }

        @Override
        public String getDisplayName() {
            return "Evaluate Groovy content into the Pipeline script";
        }
    }

}
