package org.jenkinsci.plugins.workflow.cps.steps.ingroovy

/**
 * Access to the money in vault has to go through the teller.
 *
 * @see HelloWorldGroovyStep
 * @see GroovyStepTest#security()
 */
class BankTellerStepExecution extends BankTellerSecureStepExecution {
    /**
     * Reports the amount of money you have in vault
     */
    def call() {
        return moneyInVault;
    }
}
