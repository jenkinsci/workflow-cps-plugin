package org.jenkinsci.plugins.workflow.cps;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractProject;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

import javax.annotation.Nonnull;
import java.io.IOException;

/**
 * A step implementation to test snippetizer
 *
 * @author Kohsuke Kawaguchi
 */
public class RedBlackTestStep extends Recorder implements SimpleBuildStep {
    public RedBlackTestStep red, black;

    @DataBoundConstructor
    public RedBlackTestStep() {
    }

    @DataBoundSetter
    public void setRed(RedBlackTestStep v) {
        this.red = v;
    }

    @DataBoundSetter
    public void setBlack(RedBlackTestStep v) {
        this.black = v;
    }

    @Override
    public void perform(@Nonnull Run<?, ?> run, @Nonnull FilePath workspace, @Nonnull Launcher launcher, @Nonnull TaskListener listener) throws InterruptedException, IOException {
        // noop
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Extension
    @Symbol("rb")
    public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }
    }
}
