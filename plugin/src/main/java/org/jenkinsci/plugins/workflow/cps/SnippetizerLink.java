/*
 * The MIT License
 *
 * Copyright (c) 2018, CloudBees, Inc.
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

package org.jenkinsci.plugins.workflow.cps;

import hudson.Extension;
import hudson.ExtensionPoint;
import hudson.model.Item;
import hudson.model.Job;
import org.kohsuke.stapler.Stapler;
import org.kohsuke.stapler.StaplerRequest2;

import edu.umd.cs.findbugs.annotations.NonNull;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.jenkinsci.plugins.workflow.cps.Snippetizer.ACTION_URL;

/**
 * A link that will show up on the side panel of the snippet generator and other similar pages.
 * Display order is determined by extension ordinal - highest ordinal first.
 * 
 * @author Andrew Bayer
 */
public abstract class SnippetizerLink implements ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(SnippetizerLink.class.getName());

    /**
     * Get the URL this link should point to, which will be used by {@link #getDisplayUrl()}. If this is not absolute,
     * {@link #getDisplayUrl()} will link to this within the current context.
     */
    @NonNull
    public abstract String getUrl();

    /**
     * Get the actual URL to use in sidepanel.jelly. If {@link #getUrl()} is not absolute, this will try to get the
     * current Job context and return a url starting with that job's {@link Job#getUrl()} appended with {@link #getUrl()}.
     */
    @NonNull
    public final String getDisplayUrl() {
        String u = getUrl();

        try {
            if (new URI(u).isAbsolute()) {
                return u;
            }
        } catch (URISyntaxException e) {
            LOGGER.log(Level.WARNING, "Failed to parse URL for " + u, e);
            return "";
        }

        StaplerRequest2 req = Stapler.getCurrentRequest2();
        if (req == null) {
            return u;
        }

        Item i = req.findAncestorObject(Item.class);

        StringBuilder toAppend = new StringBuilder();

        toAppend.append(req.getContextPath());

        if (!req.getContextPath().endsWith("/")) {
            toAppend.append("/");
        }

        if (i == null) {
            toAppend.append(u);
        } else {
            toAppend.append(i.getUrl());

            if (!i.getUrl().endsWith("/")) {
                toAppend.append("/");
            }

            toAppend.append(u);
        }

        return toAppend.toString();
    }

    /**
     * Get the icon information for the link.
     */
    @NonNull
    public String getIcon() {
        return "icon-help icon-md";
    }

    /**
     * Get the display name for the link.
     */
    @NonNull
    public abstract String getDisplayName();

    /**
     * Check whether the link should target a new window - this defaults to false;
     */
    public boolean inNewWindow() {
        return false;
    }

    @Extension(ordinal = 1000L)
    public static class GeneratorLink extends SnippetizerLink {
        @Override
        @NonNull
        public String getUrl() {
            return ACTION_URL;
        }

        @Override
        @NonNull
        public String getIcon() {
            return "icon-gear2 icon-md";
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.SnippetizerLink_GeneratorLink_displayName();
        }
    }

    @Extension(ordinal = 900L)
    public static class StepReferenceLink extends SnippetizerLink {
        @Override
        @NonNull
        public String getUrl() {
            return ACTION_URL + "/html";
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.SnippetizerLink_StepReferenceLink_displayName();
        }
    }

    @Extension(ordinal = 800L)
    public static class GlobalsReferenceLink extends SnippetizerLink {
        @Override
        @NonNull
        public String getUrl() {
            return ACTION_URL + "/globals";
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.SnippetizerLink_GlobalsReferenceLink_displayName();
        }
    }

    @Extension(ordinal = 700L)
    public static class OnlineDocsLink extends SnippetizerLink {
        @Override
        @NonNull
        public String getUrl() {
            return "https://jenkins.io/doc/pipeline/";
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.SnippetizerLink_OnlineDocsLink_displayName();
        }

        @Override
        public boolean inNewWindow() {
            return true;
        }
    }

    @Extension(ordinal = 600L)
    public static class ExamplesLink extends SnippetizerLink {

        @NonNull
        @Override
        public String getUrl() {
            return "https://jenkins.io/doc/pipeline/examples/";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.SnippetizerLink_ExamplesLink_displayName();
        }
    }

    @Extension(ordinal = 500L)
    public static class GDSLLink extends SnippetizerLink {
        @Override
        @NonNull
        public String getUrl() {
            return ACTION_URL + "/gdsl";
        }

        @Override
        @NonNull
        public String getDisplayName() {
            return Messages.SnippetizerLink_GDSLLink_displayName();
        }

        @Override
        public boolean inNewWindow() {
            return true;
        }
    }
}
