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

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardUsernameCredentials;
import com.cloudbees.plugins.credentials.common.StandardUsernamePasswordCredentials;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import com.cloudbees.plugins.credentials.domains.SchemeRequirement;
import com.cloudbees.plugins.credentials.impl.BaseStandardCredentials;
import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.util.Secret;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.springframework.security.core.Authentication;

/**
 * Exposes secret-text OAuth tokens as Git-compatible username/password credentials.
 *
 * <p>The git plugin asks the credentials subsystem for {@link StandardUsernameCredentials} when authenticating
 * HTTP(S) remotes. OAuth/PAT credentials are commonly stored in Jenkins as {@link StringCredentials}, so without this
 * small adapter those token credentials are invisible to Pipeline jobs using {@link CpsScmFlowDefinition}.
 */
@Extension
public class GitOAuthTokenCredentialsProvider extends CredentialsProvider {

    static final String OAUTH_USERNAME = "oauth2";

    @Override
    public <C extends com.cloudbees.plugins.credentials.Credentials> List<C> getCredentialsInItem(
            Class<C> type, Item item, Authentication authentication, List<DomainRequirement> domainRequirements) {
        return getCredentials(type, item, authentication, domainRequirements);
    }

    @Override
    public <C extends com.cloudbees.plugins.credentials.Credentials> List<C> getCredentialsInItemGroup(
            Class<C> type,
            ItemGroup itemGroup,
            Authentication authentication,
            List<DomainRequirement> domainRequirements) {
        return getCredentials(type, itemGroup, authentication, domainRequirements);
    }

    private <C extends com.cloudbees.plugins.credentials.Credentials> List<C> getCredentials(
            Class<C> type,
            Object context,
            Authentication authentication,
            List<DomainRequirement> domainRequirements) {
        if (!isGitHttpCredentialLookup(type, domainRequirements)) {
            return Collections.emptyList();
        }

        List<StringCredentials> tokens;
        if (context instanceof Item item) {
            tokens = CredentialsProvider.lookupCredentialsInItem(
                    StringCredentials.class, item, authentication, domainRequirements);
        } else if (context instanceof ItemGroup itemGroup) {
            tokens = CredentialsProvider.lookupCredentialsInItemGroup(
                    StringCredentials.class, itemGroup, authentication, domainRequirements);
        } else {
            return Collections.emptyList();
        }

        List<C> adapted = new ArrayList<>(tokens.size());
        for (StringCredentials token : tokens) {
            adapted.add(type.cast(new OAuthTokenUsernamePasswordCredentials(token)));
        }
        return adapted;
    }

    private static boolean isGitHttpCredentialLookup(
            Class<? extends com.cloudbees.plugins.credentials.Credentials> type,
            List<DomainRequirement> domainRequirements) {
        return StandardUsernameCredentials.class.isAssignableFrom(type)
                && type.isAssignableFrom(OAuthTokenUsernamePasswordCredentials.class)
                && hasHttpScheme(domainRequirements);
    }

    private static boolean hasHttpScheme(List<DomainRequirement> domainRequirements) {
        if (domainRequirements == null) {
            return false;
        }
        for (DomainRequirement requirement : domainRequirements) {
            if (requirement instanceof SchemeRequirement schemeRequirement) {
                String scheme = schemeRequirement.getScheme();
                return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
            }
        }
        return false;
    }

    private static final class OAuthTokenUsernamePasswordCredentials extends BaseStandardCredentials
            implements StandardUsernamePasswordCredentials {

        private final StringCredentials delegate;

        OAuthTokenUsernamePasswordCredentials(StringCredentials delegate) {
            super(delegate.getScope(), delegate.getId(), delegate.getDescription());
            this.delegate = delegate;
        }

        @Override
        public String getUsername() {
            return OAUTH_USERNAME;
        }

        @Override
        public boolean isUsernameSecret() {
            return false;
        }

        @Override
        public Secret getPassword() {
            return delegate.getSecret();
        }
    }
}
