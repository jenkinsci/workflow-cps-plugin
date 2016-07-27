package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import com.trilead.ssh2.util.IOUtils;
import hudson.Extension;
import hudson.ExtensionComponent;
import hudson.ExtensionFinder;
import hudson.model.Descriptor;
import hudson.model.Hudson;
import jenkins.ExtensionComponentSet;
import jenkins.ExtensionRefreshException;
import org.jenkinsci.plugins.workflow.cps.steps.ingroovy.StepInGroovy.StepDescriptorInGroovy;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

import static java.util.logging.Level.*;

/**
 * Discover step implementations in Groovy.
 *
 * @see 'doc/step-in-groovy.md'
 * @author Kohsuke Kawaguchi
 */
@Extension
public class ExtensionFinderImpl extends ExtensionFinder {
    @Override
    public ExtensionComponentSet refresh() throws ExtensionRefreshException {
        // TODO: this is not complex. just bit tedious.
        return ExtensionComponentSet.EMPTY;
    }

    @Override
    public <T> Collection<ExtensionComponent<T>> find(Class<T> type, Hudson jenkins) {
        if (type==StepDescriptor.class)
            return (List)discover();
        else
            return Collections.emptyList();
    }

    private List<ExtensionComponent<StepDescriptorInGroovy>> discover() {
        // we want new content root for trusted CPS compiled source tree
        List<ExtensionComponent<StepDescriptorInGroovy>> r = new ArrayList<>();
        for (URL root : GroovyCompiler.get().getContentRoots()) {
            URL u;
            try {
                u = new URL(root, "META-INF/index");
            } catch (MalformedURLException e) {
                LOGGER.log(WARNING, "Failed to resolve step index from " + root);
                continue;
            }
            InputStream fin;

            try {
                fin = u.openStream();
                if (fin==null)      continue;
            } catch (IOException e) {
                // if not found, ignore this one
                continue;
            }

            try {
                try (Reader ior = new InputStreamReader(fin, "UTF-8");
                     BufferedReader br = new BufferedReader(ior)) {

                    String fqcn;
                    while ((fqcn = br.readLine()) != null) {
                        fqcn = fqcn.trim();
                        if (fqcn.startsWith("#"))   continue; // comment
                        if (fqcn.isEmpty())         continue; // empty line

                        r.add(new ExtensionComponent<>(new StepDescriptorInGroovy(fqcn), 0));
                    }
                }
            } catch (IOException e) {
                LOGGER.log(WARNING, "Failed to read " + u, e);
            } finally {
                IOUtils.closeQuietly(fin);
            }
        }
        return r;
    }

    private static final Logger LOGGER = Logger.getLogger(ExtensionFinderImpl.class.getName());
}
