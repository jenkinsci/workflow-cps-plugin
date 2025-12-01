package org.jenkinsci.plugins.workflow.cps;

import static io.jenkins.plugins.casc.misc.Util.getSecurityRoot;
import static io.jenkins.plugins.casc.misc.Util.toStringFromYamlFile;
import static io.jenkins.plugins.casc.misc.Util.toYamlString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.jenkins.plugins.casc.ConfigurationContext;
import io.jenkins.plugins.casc.ConfiguratorRegistry;
import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.model.CNode;
import org.jenkinsci.plugins.scriptsecurity.scripts.ScriptApproval;
import org.junit.ClassRule;
import org.junit.Test;

public class JcascTest {

    @ClassRule(order = 1)
    @ConfiguredWithCode("casc_test.yaml")
    public static JenkinsConfiguredWithCodeRule j = new JenkinsConfiguredWithCodeRule();

    /**
     * Check that CASC for security.cps.hideSandbox is sending the value to ScriptApproval.get().isForceSandbox()
     * @throws Exception
     */
    @Test
    public void cascHideSandBox() throws Exception {
        assertTrue(ScriptApproval.get().isForceSandbox());
    }

    @Test
    public void cascExport() throws Exception {
        ConfiguratorRegistry registry = ConfiguratorRegistry.get();
        ConfigurationContext context = new ConfigurationContext(registry);
        CNode yourAttribute = getSecurityRoot(context).get("cps");
        String exported = toYamlString(yourAttribute);
        String expected = toStringFromYamlFile(this, "casc_test_expected.yaml");
        assertEquals(exported, expected);
    }
}
