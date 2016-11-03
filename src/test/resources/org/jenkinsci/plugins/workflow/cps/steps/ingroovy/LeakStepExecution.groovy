package org.jenkinsci.plugins.workflow.cps.steps.ingroovy

class LeakStepExecution extends GroovyStepExecution {

    def call() {
        GroovyStepTest.register(this)
    }

}
