
package org.jenkinsci.plugins.workflow.cps;

import hudson.ExtensionPoint;
import hudson.ExtensionList;
import jenkins.model.Jenkins; 

public abstract class AbstractMismatchWhitelist implements ExtensionPoint {
    public Boolean ignoreCpsMismatch(String expectedReceiver, String expectedMethodName, String actualReceiver, String actualMethodName){
        return false; 
    }

    public static ExtensionList<AbstractMismatchWhitelist> all(){
        return Jenkins.getActiveInstance().getExtensionList(AbstractMismatchWhitelist.class);
    }
}