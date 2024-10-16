package org.jenkinsci.plugins.workflow.cps;

import jenkins.model.CauseOfInterruption;

public class ShutdownInterruption extends CauseOfInterruption {

	@Override
	public String getShortDescription() {
		return "Jenkins needs to terminate the execusion of this builds";
	}

}
