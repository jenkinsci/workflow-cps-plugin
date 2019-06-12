/*
 * The MIT License
 *
 * Copyright 2019 Coveo Solutions Inc.
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

import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.common.UsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.URIRequirementBuilder;
import hudson.Extension;
import hudson.Util;
import hudson.model.*;
import hudson.scm.SCM;
import hudson.scm.SCMDescriptor;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpHeaders;
import org.apache.http.client.methods.HttpGet;
import org.jenkinsci.plugins.workflow.cps.persistence.PersistIn;
import org.jenkinsci.plugins.workflow.flow.*;
import org.kohsuke.stapler.*;
import io.jenkins.plugins.httpclient.RobustHTTPClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.jenkinsci.plugins.workflow.cps.persistence.PersistenceContext.JOB;

@PersistIn(JOB) public class CpsHttpFlowDefinition extends FlowDefinition {

    private final String scriptUrl;
    private final int retryCount;
    private String credentialsId;

    @DataBoundConstructor public CpsHttpFlowDefinition(String scriptUrl, int retryCount) {
        this.scriptUrl = scriptUrl.trim();
        this.retryCount = retryCount;
    }

    public String getScriptUrl() {
        return scriptUrl;
    }

    public int getRetryCount() {
        return retryCount;
    }

    @DataBoundSetter public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmpty(credentialsId);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @Override public CpsFlowExecution create(FlowExecutionOwner owner, TaskListener listener, List<? extends Action> actions)
            throws Exception {
        Queue.Executable _build = owner.getExecutable();
        if (!(_build instanceof Run)) {
            throw new IOException("Can only pull a Jenkinsfile in a run");
        }
        Run<?, ?> build = (Run<?, ?>) _build;

        String expandedScriptUrl = build.getEnvironment(listener).expand(scriptUrl);
        listener.getLogger().println("Fetching pipeline from " + expandedScriptUrl);

        RobustHTTPClient client = new RobustHTTPClient();
        client.setStopAfterAttemptNumber(retryCount + 1);

        HttpGet httpGet = new HttpGet(expandedScriptUrl);
        if (credentialsId != null) {
            UsernamePasswordCredentials credentials = CredentialsMatchers.firstOrNull(CredentialsProvider.lookupCredentials(UsernamePasswordCredentials.class, Jenkins.getInstance(), ACL.SYSTEM, Collections.emptyList()), CredentialsMatchers.withId(credentialsId));
            if (credentials != null) {
                String encoded = Base64.getEncoder().encodeToString((credentials.getUsername() + ":"
                        + credentials.getPassword()).getBytes(StandardCharsets.UTF_8));
                httpGet.setHeader(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
                CredentialsProvider.track(build, credentials);
            }
        }

        AtomicReference<CpsFlowExecution> result = new AtomicReference<CpsFlowExecution>(null);

        client.connect("get pipeline", "get pipeline from " + expandedScriptUrl, c -> c.execute(httpGet), response ->  {
            try (InputStream is = response.getEntity().getContent()) {
                String script = IOUtils.toString(is, "UTF-8");
                Queue.Executable queueExec = owner.getExecutable();
                FlowDurabilityHint hint = (queueExec instanceof Run) ?
                        DurabilityHintProvider.suggestedFor(((Run) queueExec).getParent()) :
                        GlobalDefaultFlowDurabilityLevel.getDefaultDurabilityHint();
                result.set(new CpsFlowExecution(script, true, owner, hint));
            }
        }, listener);

        return result.get();
    }

    @Extension public static class DescriptorImpl extends FlowDefinitionDescriptor {

        @Override public String getDisplayName() {
            return "Pipeline script from HTTP";
        }

        public Collection<? extends SCMDescriptor<?>> getApplicableDescriptors() {
            StaplerRequest req = Stapler.getCurrentRequest();
            Job<?, ?> job = req != null ? req.findAncestorObject(Job.class) : null;
            return SCM._for(job);
        }

        public ListBoxModel doFillCredentialsIdItems(@QueryParameter String scriptUrl, @QueryParameter String credentialsId) {
            return new StandardListBoxModel().includeEmptyValue().includeMatchingAs(ACL.SYSTEM, Jenkins.getInstance(), StandardUsernamePasswordCredentials.class, URIRequirementBuilder.fromUri(scriptUrl).build(), CredentialsMatchers.always()).includeCurrentValue(credentialsId);
        }

    }

}
