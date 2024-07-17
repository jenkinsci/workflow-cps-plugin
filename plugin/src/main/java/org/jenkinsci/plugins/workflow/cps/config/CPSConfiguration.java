package org.jenkinsci.plugins.workflow.cps.config;

import hudson.Extension;
import hudson.ExtensionList;
import jenkins.model.GlobalConfiguration;
import org.jenkinsci.Symbol;

@Extension
@Symbol("cpsGlobalConfiguration")
public class CPSConfiguration extends GlobalConfiguration {

    /**
     * Whether to show the sandbox checkbox in jobs to users without {@link jenkins.model.Jenkins.ADMINISTER}
     */
    private boolean hideSandbox;

    public CPSConfiguration() {
        load();
    }

    public boolean isHideSandbox() {
        return hideSandbox;
    }

    public void setHideSandbox(boolean hideSandbox) {
        this.hideSandbox = hideSandbox;
        save();
    }

    public static CPSConfiguration get() {
        return ExtensionList.lookupSingleton(CPSConfiguration.class);
    }
}
