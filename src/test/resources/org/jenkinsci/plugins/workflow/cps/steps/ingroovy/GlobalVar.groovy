package org.jenkinsci.plugins.workflow.cps.steps.ingroovy

// compare Docker.groovy for example
class GlobalVar implements Serializable {

    private org.jenkinsci.plugins.workflow.cps.CpsScript script
    private String field

    public GlobalVar(org.jenkinsci.plugins.workflow.cps.CpsScript script) {
        this.script = script
    }

    @NonCPS public void setField(String newValue) {
        this.field = newValue
    }

    public void show() {
        script.echo "field is currently ${field} and environment variable is ${script.env.PROP}"
    }

}
