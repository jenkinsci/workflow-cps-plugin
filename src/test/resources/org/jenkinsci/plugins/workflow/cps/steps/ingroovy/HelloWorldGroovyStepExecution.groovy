package org.jenkinsci.plugins.workflow.cps.steps.ingroovy

/**
 *
 */
class HelloWorldGroovyStepExecution extends GroovyStepExecution {
    def call() {
        echo "Hello ${step.message}"
    }
}
