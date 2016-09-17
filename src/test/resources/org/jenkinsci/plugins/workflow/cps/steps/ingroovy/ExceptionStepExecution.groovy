package org.jenkinsci.plugins.workflow.cps.steps.ingroovy

/**
 * @see StepInGroovyTest#exception()
 */
class ExceptionStepExecution extends GroovyStepExecution {
    def call(Closure body) {
        switch(step.mode) {
        case 'fromGroovyStepToCaller':
            throw new LifeIsToughException("Jesse wants so many test cases");
        case 'passThrough':
            return body()
        case 'fromBodyToGroovyStep':
            try {
                body()
                fail;
            } catch (LifeIsToughException e) {
                return e.message;
            }
        }
    }
}
