package org.jenkinsci.plugins.workflow.cps.steps.ingroovy

public class UsesGlobalVarStepExecution extends GroovyStepExecution {

    def call() {
        env.PROP = 'defined'
        globalVar.show()
    }

}
