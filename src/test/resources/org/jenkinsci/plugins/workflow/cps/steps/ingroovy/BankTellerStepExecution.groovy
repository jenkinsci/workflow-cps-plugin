package org.jenkinsci.plugins.workflow.cps.steps.ingroovy

/**
 * Access to the money in vault has to go through the teller.
 *
 * @see HelloWorldGroovyStep
 * @see GroovyStepTest#security()
 */
public class BankTellerStepExecution extends GroovyStepExecution {
    /**
     * Even though this is public, it shouldn't be accessible
     */
    public static int moneyInVault = 2000;

    /**
     * Reports the amount of money you have in vault
     */
    def call() {
        return moneyInVault;
    }
}
