package org.jenkinsci.plugins.workflow.cps;

import com.gargoylesoftware.htmlunit.HttpMethod;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.WebResponse;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import hudson.model.Describable;
import hudson.model.Queue;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.jenkinsci.plugins.structs.describable.ArrayType;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.DescribableParameter;
import org.jenkinsci.plugins.structs.describable.ErrorType;
import org.jenkinsci.plugins.structs.describable.HeterogeneousObjectType;
import org.jenkinsci.plugins.structs.describable.HomogeneousObjectType;
import org.jenkinsci.plugins.structs.describable.ParameterType;
import org.jenkinsci.plugins.workflow.flow.FlowExecution;
import org.jenkinsci.plugins.workflow.flow.FlowExecutionOwner;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jvnet.hudson.test.JenkinsRule;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.jenkinsci.plugins.workflow.util.StaplerReferer;

import static org.junit.Assert.*;

/**
 * Test harness to test {@link Snippetizer}.
 */
public class SnippetizerTester {

    private final JenkinsRule r;

    /**
     * This helper requires {@link JenkinsRule}.
     */
    public SnippetizerTester(JenkinsRule r) {
        this.r = r;
    }

    /**
     * Tests a form submitting part of snippetizer.
     *
     * @param json
     *      The form submission value from the configuration page to be tested.
     * @param responseText
     *      Expected snippet to be generated
     * @param referer
     *      needed because of {@link StaplerReferer}
     */
    public void assertGenerateSnippet(@Nonnull String json, @Nonnull String responseText, @CheckForNull String referer) throws Exception {
        JenkinsRule.WebClient wc = r.createWebClient();
        WebRequest wrs = new WebRequest(new URL(r.getURL(), Snippetizer.GENERATE_URL), HttpMethod.POST);
        if (referer != null) {
            wrs.setAdditionalHeader("Referer", referer);
        }
        List<NameValuePair> params = new ArrayList<>();
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
        assertEquals(expected, Snippetizer.step2Groovy(step));
        CompilerConfiguration cc = new CompilerConfiguration();
        cc.setScriptBaseClass(DelegatingScript.class.getName());
        GroovyShell shell = new GroovyShell(r.jenkins.getPluginManager().uberClassLoader,new Binding(),cc);

        DelegatingScript s = (DelegatingScript) shell.parse(expected);
        s.o = new DSL(new DummyOwner()) {
            // for testing, instead of executing the step just return an instantiated Step
            @Override
            protected Object invokeStep(StepDescriptor d, String name, Object args) {
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

    /**
     * Recurses through the model of a {@link Describable} class's {@link DescribableModel} and its parameters to verify that doc generation will work.
     *
     * @param describableClass
     *     A {@link Class} implementing {@link Describable}
     * @throws Exception
     *     If any errors are encountered.
     */
    @SuppressWarnings("unchecked")
    public static void assertDocGeneration(Class<? extends Describable> describableClass) throws Exception {
        DescribableModel<?> model = DescribableModel.of(describableClass);

        assertNotNull(model);

        recurseOnModel(model);

    }

    private static void recurseOnTypes(DescribableModel<?> model, ParameterType type) throws Exception {
        if (type instanceof ErrorType) {
            throw new Exception("could not describe " + model, ((ErrorType) type).getError());
        }

        if (type instanceof ArrayType) {
            recurseOnTypes(model, ((ArrayType)type).getElementType());
        } else if (type instanceof HomogeneousObjectType) {
            recurseOnModel(((HomogeneousObjectType) type).getSchemaType());
        } else if (type instanceof HeterogeneousObjectType) {
            if (((HeterogeneousObjectType) type).getActualType() == Object.class) {
                // See html.groovy#describeType. For example, JENKINS-53917 ChoiceParameterDefinition.choices.
                System.err.println("Ignoring " + model.getType().getName() + " since a parameter is not enumerable");
                return;
            }
            for (Map.Entry<String, DescribableModel<?>> entry : ((HeterogeneousObjectType) type).getTypes().entrySet()) {
                recurseOnModel(entry.getValue());
            }
        }
    }

    private static void recurseOnModel(DescribableModel<?> model) throws Exception {
        for (DescribableParameter param : model.getParameters()) {
            recurseOnTypes(model, param.getType());
        }
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
