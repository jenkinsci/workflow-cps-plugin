package org.jenkinsci.plugins.workflow.cps;

import hudson.model.Action;

/**
 * Parent class for transient {@link hudson.model.Action}s of running pipeline builds.
 * @see org.jenkinsci.plugins.workflow.cps.RunningFlowActions
 */
public abstract class RunningFlowAction implements Action {

}
