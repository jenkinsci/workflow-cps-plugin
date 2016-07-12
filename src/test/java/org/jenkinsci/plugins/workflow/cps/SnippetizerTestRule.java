package org.jenkinsci.plugins.workflow.cps;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.model.Queue;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Test harness to test snippetizer.
 */
public class SnippetizerTestRule implements TestRule {

    public final JenkinsRule r;

    /**
     * This rule should be used together with {@link JenkinsRule}
     */
    public SnippetizerTestRule(JenkinsRule r) {
        this.r = r;
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return base;
    }

    /**
     * Tests a form submitting part of snippetizer.
     *
     * @param json
     *      The form submission value from the configuration page to be tested.
     * @param responseText
     *      Expected snippet to be generated
     * @param referer
     *      (I don't understand why implementation relies on this ¯\_(ツ)_/¯
     */
    public void assertGenerateSnippet(@Nonnull String json, @Nonnull String responseText, @CheckForNull String referer) throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        WebRequest wrs = new WebRequest(new URL(r.getURL(), Snippetizer.GENERATE_URL), HttpMethod.POST);
        if (referer != null) {
            wrs.setAdditionalHeader("Referer", referer);
        }
        List<NameValuePair> params = new ArrayList<NameValuePair>();
        params.add(new NameValuePair("json", json));
        // WebClient.addCrumb *replaces* rather than *adds*:
        params.add(new NameValuePair(r.jenkins.getCrumbIssuer().getDescriptor().getCrumbRequestField(), r.jenkins.getCrumbIssuer().getCrumb(null)));
        wrs.setRequestParameters(params);
        WebResponse response = wc.getPage(wrs).getWebResponse();
        assertEquals("text/plain", response.getContentType());
        assertEquals(responseText, response.getContentAsString().trim());
    }

    /**
     * Given a fully configured {@link Step}, make sure the output from the snippetizer matches the expected value.
     *
     * <p>
     * As an additional measure, this method also executes the generated snippet and makes sure
     * it yields identical {@link Step} object.
     *
     * @param step
     *      A fully configured step object
     * @param expected
     *      Expected snippet to be generated.
     */
    public void assertRoundTrip(Step step, String expected) throws Exception {
        assertEquals(expected, Snippetizer.object2Groovy(step));
        CompilerConfiguration cc = new CompilerConfiguration();
        cc.setScriptBaseClass(DelegatingScript.class.getName());
        GroovyShell shell = new GroovyShell(r.jenkins.getPluginManager().uberClassLoader,new Binding(),cc);

        DelegatingScript s = (DelegatingScript) shell.parse(expected);
        s.o = new DSL(new DummyOwner()) {
            // for testing, instead of executing the step just return an instantiated Step
            @Override
            protected Object invokeStep(StepDescriptor d, Object args) {
                try {
                    return d.newInstance(parseArgs(args, d).namedArgs);
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
            }
        };

        Step actual = (Step) s.run();
        r.assertEqualDataBoundBeans(step, actual);
    }

    private static class DummyOwner extends FlowExecutionOwner {
        DummyOwner() {}
        @Override public FlowExecution get() throws IOException {
            return null;
        }
        @Override public File getRootDir() throws IOException {
            throw new IOException("not implemented");
        }
        @Override public Queue.Executable getExecutable() throws IOException {
            throw new IOException("not implemented");
        }
        @Override public String getUrl() throws IOException {
            throw new IOException("not implemented");
        }
        @Override public boolean equals(Object o) {
            return true;
        }
        @Override public int hashCode() {
            return 0;
        }
    }
}
