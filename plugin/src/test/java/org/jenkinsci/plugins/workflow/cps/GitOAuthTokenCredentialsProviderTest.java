/*
 * The MIT License
 *
 * Copyright 2026.
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.cloudbees.plugins.credentials.Credentials;
import com.cloudbees.plugins.credentials.CredentialsMatchers;
import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.SystemCredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import hudson.security.ACL;
import hudson.util.Secret;
import java.util.List;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

public class GitOAuthTokenCredentialsProviderTest {

    @Rule
    public JenkinsRule r = new JenkinsRule();

    @Test
    public void exposesSecretTextTokenAsGitHttpCredentials() throws Exception {
        addOAuthToken();
        WorkflowJob job = r.createProject(WorkflowJob.class);

        List<StandardUsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentialsInItem(
                StandardUsernamePasswordCredentials.class,
                job,
                ACL.SYSTEM2,
                List.of(new SchemeRequirement("https")));

        StandardUsernamePasswordCredentials token = credentials.stream()
                .filter(c -> "oauth-token".equals(c.getId()))
                .findFirst()
                .orElseThrow();
        assertEquals(GitOAuthTokenCredentialsProvider.OAUTH_USERNAME, token.getUsername());
        assertEquals("token-value", token.getPassword().getPlainText());
    }

    @Test
    public void findsSecretTextTokenByCredentialsIdForGitHttpCredentials() throws Exception {
        addOAuthToken();
        WorkflowJob job = r.createProject(WorkflowJob.class);

        List<StandardUsernamePasswordCredentials> credentials = CredentialsProvider.lookupCredentialsInItem(
                StandardUsernamePasswordCredentials.class,
                job,
                ACL.SYSTEM2,
                List.of(new SchemeRequirement("https")));
        StandardUsernamePasswordCredentials token =
                CredentialsMatchers.firstOrNull(credentials, CredentialsMatchers.withId("oauth-token"));

        assertNotNull(token);
        assertEquals(GitOAuthTokenCredentialsProvider.OAUTH_USERNAME, token.getUsername());
        assertEquals("token-value", token.getPassword().getPlainText());
    }

    @Test
    public void doesNotExposeOAuthTokensForSshGitCredentials() throws Exception {
        addOAuthToken();
        WorkflowJob job = r.createProject(WorkflowJob.class);

        List<String> ids = CredentialsProvider.lookupCredentialsInItem(
                        StandardUsernamePasswordCredentials.class,
                        job,
                        ACL.SYSTEM2,
                        List.of(new SchemeRequirement("ssh")))
                .stream()
                .map(StandardUsernamePasswordCredentials::getId)
                .toList();

        assertThat(ids, not(hasItem("oauth-token")));
    }

    @Test
    public void doesNotExposeOAuthTokensForBroadCredentialLookups() throws Exception {
        addOAuthToken();
        WorkflowJob job = r.createProject(WorkflowJob.class);

        List<Credentials> credentials = CredentialsProvider.lookupCredentialsInItem(
                Credentials.class, job, ACL.SYSTEM2, List.of(new SchemeRequirement("https")));
        List<String> adaptedIds = credentials.stream()
                .filter(StandardUsernamePasswordCredentials.class::isInstance)
                .map(StandardUsernamePasswordCredentials.class::cast)
                .map(StandardUsernamePasswordCredentials::getId)
                .toList();

        assertThat(adaptedIds, not(hasItem("oauth-token")));
    }

    private static void addOAuthToken() throws Exception {
        SystemCredentialsProvider.getInstance()
                .getCredentials()
                .add(new StringCredentialsImpl(
                        CredentialsScope.GLOBAL, "oauth-token", "OAuth token for Git", Secret.fromString("token-value")));
        SystemCredentialsProvider.getInstance().save();
    }
}
