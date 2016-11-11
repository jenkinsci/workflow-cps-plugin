package org.jenkinsci.plugins.workflow.cps.nodes;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import hudson.Extension;
import hudson.ExtensionListListener;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import java.util.concurrent.ExecutionException;

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
        descriptorCache.invalidateAll();
    }

    private static transient final LoadingCache<String,StepDescriptor> descriptorCache = CacheBuilder.newBuilder().maximumSize(1000).build(new CacheLoader<String,StepDescriptor>() {
        @Override public StepDescriptor load(String descriptorId) {
            if (descriptorId != null) {
                Jenkins j = Jenkins.getInstance();
                if (j != null) {
                    return (StepDescriptor) j.getDescriptor(descriptorId);
                }
            }
            return null;
        }
    });

    @CheckForNull
    public StepDescriptor getDescriptor(String descriptorId) {
        if (descriptorId != null) {
            try {
                return descriptorCache.get(descriptorId);
            } catch (ExecutionException exec) {
                throw new RuntimeException(exec);  // If we can't get the descriptor good grief
            }

        }
        return null;
    }
}
