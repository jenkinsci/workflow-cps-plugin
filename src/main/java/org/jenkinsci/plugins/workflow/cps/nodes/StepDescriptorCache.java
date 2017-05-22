/*
 * The MIT License
 *
 * Copyright (c) 2016, CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.cps.nodes;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.util.Memoizer;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;

import static org.jenkinsci.plugins.workflow.cps.nodes.NullStepDescriptorHolder.nulllDescriptor;

/**
 * Shared cacheSingleton for the StepDescriptors, extension-scoped to avoid test issues
 */
@Extension
@Restricted(NoExternalUse.class)
public class StepDescriptorCache implements ExtensionPoint {

    public static StepDescriptorCache getPublicCache() {
        return Jenkins.getActiveInstance().getExtensionList(StepDescriptorCache.class).get(0);
    }

    public StepDescriptorCache() {}

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED)  // Prevents potential leakage on reload
    public static void invalidateGlobalCache() {
        getPublicCache().invalidateAll();
    }

    public void invalidateAll() {
        descriptorCache.clear();
    }

    private final Memoizer<String, StepDescriptor> descriptorCache = new Memoizer<String, StepDescriptor>() {

        public StepDescriptor compute(String descriptorId) {
            Jenkins j = Jenkins.getInstance();
            StepDescriptor descriptor=null;
            if (descriptorId != null && j != null) {
                descriptor= (StepDescriptor) j.getDescriptor(descriptorId);
            }
            if(descriptor==null){
                descriptor=nulllDescriptor;
            }
            return descriptor;
        }
    };

    @CheckForNull
    public StepDescriptor getDescriptor(String descriptorId) {
        StepDescriptor descriptor = null;
        if (descriptorId != null) {
            descriptor = descriptorCache.get(descriptorId);
        }
        if (descriptor == nulllDescriptor ) {
            return null;
        } else {
            return descriptor;
        }
    }

}
