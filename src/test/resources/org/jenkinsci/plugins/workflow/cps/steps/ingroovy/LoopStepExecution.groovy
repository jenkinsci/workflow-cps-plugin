package org.jenkinsci.plugins.workflow.cps.steps.ingroovy

/**
 * Invokes body multiple times
 *
 * @see HelloWorldGroovyStep
 */
class LoopStepExecution extends GroovyStepExecution {
    def call(Closure body) {
        if (step.parallel) {
            def branches = [:]
            for (int i=0; i<step.count; i++) {
                branches["branch$i"] = body;
            }
            parallel branches;
        } else {
            for (int i=0; i<step.count; i++) {
                body();
            }
        }
    }
}
