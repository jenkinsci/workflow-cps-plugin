package org.jenkinsci.plugins.workflow.cps.steps.ingroovy

import org.apache.commons.lang.StringUtils

class ComplexStepExecution extends GroovyStepExecution {
    /**
     * Store some value in instance field
     */
    def field;

    /**
     * Exercises additional functions aside from call()
     */
    def sum() {
        sh 'echo sum=$(('+StringUtils.join(step.numbers,'+')+'))'
    }

    def call(body) {
        echo "parameterName=${step.param.name}"
        node {
            sum()
            field = body()
        }
        return field;
    }
}
