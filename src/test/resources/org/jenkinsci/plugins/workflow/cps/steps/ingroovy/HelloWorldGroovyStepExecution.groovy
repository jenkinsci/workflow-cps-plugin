package org.jenkinsci.plugins.workflow.cps.steps.ingroovy

/**
 * @see GroovyStepTest#helloWorld()
 */
class HelloWorldGroovyStepExecution extends GroovyStepExecution {
    def call(Closure body) {
        echo "Hello ${step.message}"
        body();
        echo "Good bye ${step.message}"
    }
}
