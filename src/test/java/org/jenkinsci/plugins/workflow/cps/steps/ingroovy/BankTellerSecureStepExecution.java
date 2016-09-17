package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

/**
 * Supposed to be only accessible to {@link BankTellerStepExecution}
 * but not to untrusted Jenkinsfile.
 *
 * @author Kohsuke Kawaguchi
 * @see GroovyStepTest#security()
 */
public abstract class BankTellerSecureStepExecution extends GroovyStepExecution {
    public int moneyInVault = 2000;
}
