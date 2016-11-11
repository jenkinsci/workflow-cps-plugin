package org.jenkinsci.plugins.workflow.cps.nodes;

import hudson.Extension;
import hudson.ExtensionListListener;
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

    // This ensures we use extension loading unless running a pure unit test with no Jenkins
    private static StepDescriptorCache getCacheInstance() {
        Jenkins myJenkins = Jenkins.getInstance();
        if ( myJenkins == null) {
            return new StepDescriptorCache();
        } else {
            return myJenkins.getExtensionList(StepDescriptorCache.class).get(0);
        }
    }

    private static final StepDescriptorCache cacheSingleton = StepDescriptorCache.getCacheInstance();

    public static StepDescriptorCache getPublicCache() {
        return cacheSingleton;
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

    private final ConcurrentHashMap<String, StepDescriptor> descriptorCache = new ConcurrentHashMap<String, StepDescriptor>() {
        public StepDescriptor get(String descriptorId) {
            StepDescriptor output = super.get(descriptorId);
            if (output == null) {
                output = load(descriptorId);
                if (output != null) {
                    this.put(descriptorId, output); // We may get redundant writes but that is ok
                }
            }
            return output;
        }

        public StepDescriptor load(String descriptorId) {
            Jenkins j = Jenkins.getInstance();
            if (j != null) {
                return (StepDescriptor) j.getDescriptor(descriptorId);
            } else {
                return null;
            }
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
