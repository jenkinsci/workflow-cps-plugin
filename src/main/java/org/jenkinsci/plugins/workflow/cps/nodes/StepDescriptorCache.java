package org.jenkinsci.plugins.workflow.cps.nodes;

import hudson.Extension;
import hudson.ExtensionListListener;
import hudson.util.Memoizer;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared cacheSingleton for the StepDescriptors, extension-scoped to avoid test issues
 */
@Extension
@Restricted(NoExternalUse.class)
public class StepDescriptorCache {

    /** Used ONLY for unit tests where there isn't actually a running Jenkins */
    private static final StepDescriptorCache fallbackSingleton = new StepDescriptorCache();

    public static StepDescriptorCache getPublicCache() {
        Jenkins myJenkins = Jenkins.getInstance();
        if ( myJenkins == null) {
            return fallbackSingleton;
        } else {
            return myJenkins.getExtensionList(StepDescriptorCache.class).get(0);
        }
    }

    ExtensionListListener myListener = new ExtensionListListener() {
        @Override
        public void onChange() {
            invalidateAll();
        }
    };

    public StepDescriptorCache() {
        // Ensures we purge the cache if steps change
        Jenkins.getInstance().getExtensionList(StepDescriptor.class).addListener(myListener);
    }

    public void invalidateAll() {
        descriptorCache.clear();
    }

    private final Memoizer<String, StepDescriptor> descriptorCache = new Memoizer<String, StepDescriptor>() {

        public StepDescriptor compute(String descriptorId) {
            Jenkins j = Jenkins.getInstance();
            if (descriptorId != null && j != null) {
                return (StepDescriptor) j.getDescriptor(descriptorId);
            }
            return null;
        }
    };

    @CheckForNull
    public StepDescriptor getDescriptor(String descriptorId) {
        if (descriptorId != null) {
            return descriptorCache.get(descriptorId);
        }
        return null;
    }
}
