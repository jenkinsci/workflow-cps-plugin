/*
 * The MIT License
 *
 * Copyright 2022 CloudBees, Inc.
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

import edu.umd.cs.findbugs.annotations.CheckForNull;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.ExtensionPoint;
import hudson.Main;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.util.SystemProperties;

/**
 * Determines what Groovy source files can be loaded in Pipelines.
 *
 * In Pipeline, the standard behavior of {@code GroovyClassLoader} would allow Groovy source files from core or plugins
 * to be loaded as long as they are somewhere on the classpath. This includes things like Groovy views, which are not
 * intended to be available to pipelines. When these files are loaded, they are loaded by the trusted
 * {@link CpsGroovyShell} and are not sandbox-transformed, which means that allowing arbitrary Groovy source files to
 * be loaded is potentially unsafe.
 *
 * {@link ClassLoaderImpl} blocks all Groovy source files from being loaded by default unless they are allowed by an
 * implementation of this extension point.
 */
public abstract class GroovySourceFileAllowlist implements ExtensionPoint {
    private static final Logger LOGGER = Logger.getLogger(GroovySourceFileAllowlist.class.getName());
    private static final String DISABLED_PROPERTY = GroovySourceFileAllowlist.class.getName() + ".DISABLED";
    static boolean DISABLED = SystemProperties.getBoolean(DISABLED_PROPERTY);

    /**
     * Checks whether a given Groovy source file is allowed to be loaded by {@link CpsFlowExecution#getTrustedShell}.
     *
     * @param groovySourceFileUrl the absolute URL to the Groovy source file as returned by {@link ClassLoader#getResource}
     * @return {@code true} if the Groovy source file may be loaded, {@code false} otherwise
     */
    public abstract boolean isAllowed(String groovySourceFileUrl);

    public static List<GroovySourceFileAllowlist> all() {
        return ExtensionList.lookup(GroovySourceFileAllowlist.class);
    }

    /**
     * {@link ClassLoader} that acts normally except for returning {@code null} from {@link #getResource} and
     * {@link #getResources} when looking up Groovy source files if the files are not allowed by
     * {@link GroovySourceFileAllowlist}.
     */
    static class ClassLoaderImpl extends ClassLoader {
        private static final String LOG_MESSAGE_TEMPLATE =
                "Preventing {0} from being loaded without sandbox protection in {1}. "
                        + "To allow access to this file, add any suffix of its URL to the system property ‘"
                        + DefaultAllowlist.ALLOWED_SOURCE_FILES_PROPERTY
                        + "’ (use commas to separate multiple files). If you "
                        + "want to allow any Groovy file on the Jenkins classpath to be accessed, you may set the system "
                        + "property ‘"
                        + DISABLED_PROPERTY + "’ to true.";

        private final String owner;

        public ClassLoaderImpl(@CheckForNull CpsFlowExecution execution, ClassLoader parent) {
            super(parent);
            this.owner = describeOwner(execution);
        }

        private static String describeOwner(@CheckForNull CpsFlowExecution execution) {
            if (execution != null) {
                try {
                    return execution.getOwner().getExecutable().toString();
                } catch (IOException e) {
                    // Not significant in this context.
                }
            }
            return "unknown";
        }

        @Override
        public URL getResource(String name) {
            URL url = super.getResource(name);
            if (DISABLED || url == null || !endsWithIgnoreCase(name, ".groovy") || isAllowed(url)) {
                return url;
            }
            // Note: This message gets printed twice because of
            // https://github.com/apache/groovy/blob/41b990d0a20e442f29247f0e04cbed900f3dcad4/src/main/org/codehaus/groovy/control/ClassNodeResolver.java#L184-L186.
            LOGGER.log(Level.WARNING, LOG_MESSAGE_TEMPLATE, new Object[] {url, owner});
            return null;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            Enumeration<URL> urls = super.getResources(name);
            if (DISABLED || !urls.hasMoreElements() || !endsWithIgnoreCase(name, ".groovy")) {
                return urls;
            }
            List<URL> filteredUrls = new ArrayList<>();
            while (urls.hasMoreElements()) {
                URL url = urls.nextElement();
                if (isAllowed(url)) {
                    filteredUrls.add(url);
                } else {
                    LOGGER.log(Level.WARNING, LOG_MESSAGE_TEMPLATE, new Object[] {url, owner});
                }
            }
            return Collections.enumeration(filteredUrls);
        }

        private static boolean isAllowed(URL url) {
            String urlString = url.toString();
            return ExtensionList.lookupSingleton(AllowedGroovyResourcesCache.class).isAllowed(urlString);
        }

        private static boolean endsWithIgnoreCase(String value, String suffix) {
            int suffixLength = suffix.length();
            return value.regionMatches(true, value.length() - suffixLength, suffix, 0, suffixLength);
        }
    }

    @Extension
    @SuppressFBWarnings(value = "NP_BOOLEAN_RETURN_NULL", justification = "intentionally not caching negative results")
    public static class AllowedGroovyResourcesCache {
        private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

        public boolean isAllowed(String groovySourceFileUrl) {
            Boolean cachedResult = cache.computeIfAbsent(groovySourceFileUrl, url -> {
                for (GroovySourceFileAllowlist allowlist : GroovySourceFileAllowlist.all()) {
                    if (allowlist.isAllowed(url)) {
                        return true;
                    }
                }
                // In practice we should only get here with files that are allowed, so we don't cache negative
                // results in case it would cause problems with unusual Pipelines that reference Groovy source
                // files directly in combination with dynamically installed plugins.
                return null;
            });
            return Boolean.TRUE.equals(cachedResult);
        }
    }

    /**
     * Allows Groovy source files used to implement DSLs in plugins that were created before
     * {@link GroovySourceFileAllowlist} was introduced.
     */
    @Extension
    public static class DefaultAllowlist extends GroovySourceFileAllowlist {
        private static final Logger LOGGER = Logger.getLogger(DefaultAllowlist.class.getName());
        private static final String ALLOWED_SOURCE_FILES_PROPERTY =
                DefaultAllowlist.class.getCanonicalName() + ".ALLOWED_SOURCE_FILES";
        /**
         * A list containing suffixes of known-good Groovy source file URLs that need to be accessible to Pipeline code.
         */
        /* Note: Actual ClassLoader resource URLs depend on environmental factors such as webroot settings and whether
         * we are currently testing one of the plugins in the list, so default-allowlist only contains the path
         * component of the resource URLs, and we allow any resource URL that ends with one of the entries in the list.
         *
         * We could try to load the exact URLs at runtime, but then we would have to account for dynamic plugin loading
         * (especially when a new Jenkins controller is initialized) and the fact that workflow-cps is always a
         * dependency of these plugins.
         */
        static final List<String> ALLOWED_SOURCE_FILES = new ArrayList<>();

        public DefaultAllowlist() throws IOException {
            // We load custom entries first to improve performance in case .groovy is used for the property.
            String propertyValue = SystemProperties.getString(ALLOWED_SOURCE_FILES_PROPERTY, "");
            for (String groovyFile : propertyValue.split(",")) {
                groovyFile = groovyFile.trim();
                if (!groovyFile.isEmpty()) {
                    if (groovyFile.endsWith(".groovy")) {
                        ALLOWED_SOURCE_FILES.add(groovyFile);
                        LOGGER.log(Level.INFO, "Allowing Pipelines to access {0}", groovyFile);
                    } else {
                        LOGGER.log(Level.WARNING, "Ignoring invalid Groovy source file: {0}", groovyFile);
                    }
                }
            }
            loadDefaultAllowlist(ALLOWED_SOURCE_FILES);
            // Some plugins use test-specific Groovy DSLs.
            if (Main.isUnitTest) {
                ALLOWED_SOURCE_FILES.addAll(
                        List.of(
                                // pipeline-model-definition
                                "/org/jenkinsci/plugins/pipeline/modeldefinition/agent/impl/LabelAndOtherFieldAgentScript.groovy",
                                "/org/jenkinsci/plugins/pipeline/modeldefinition/parser/GlobalStageNameTestConditionalScript.groovy",
                                "/org/jenkinsci/plugins/pipeline/modeldefinition/parser/GlobalStepCountTestConditionalScript.groovy"));
            }
        }

        private static void loadDefaultAllowlist(List<String> allowlist) throws IOException {
            try (InputStream is = GroovySourceFileAllowlist.class.getResourceAsStream(
                            "GroovySourceFileAllowlist/default-allowlist");
                    BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        allowlist.add(line);
                    }
                }
            }
        }

        @Override
        public boolean isAllowed(String groovySourceFileUrl) {
            for (String sourceFile : ALLOWED_SOURCE_FILES) {
                if (groovySourceFileUrl.endsWith(sourceFile)) {
                    return true;
                }
            }
            return false;
        }
    }
}
