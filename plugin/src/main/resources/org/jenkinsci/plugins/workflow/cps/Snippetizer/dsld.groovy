package org.jenkinsci.plugins.workflow.cps.Snippetizer

import groovy.json.StringEscapeUtils
import hudson.FilePath
import hudson.Functions
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.workflow.cps.GlobalVariable
import org.jenkinsci.plugins.workflow.cps.Snippetizer
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import jenkins.model.Jenkins

Snippetizer snippetizer = my;

Map<StepDescriptor,DescribableModel> steps = new LinkedHashMap<StepDescriptor, DescribableModel>();

def errorSteps = [:]

[false, true].each { isAdvanced ->
    snippetizer.getQuasiDescriptors(isAdvanced).each { d ->
        if (!Step.isAssignableFrom(d.real.clazz)) {
            return // TODO JENKINS-37215
        }
        StepDescriptor step = Jenkins.get().getDescriptor(d.real.clazz)
        DescribableModel model
        try {
            model = new DescribableModel(d.real.clazz)
        } catch (Exception e) {
            println "Error on ${d.real.clazz}: ${Functions.printThrowable(e)}"
            errorSteps.put(d.real.clazz, e.message)
        }
        if (model != null) {
            steps.put(step, model)
        }

    }
}

def nodeContext = []
def scriptContext = []

steps.each { StepDescriptor step, DescribableModel model ->
    def params = [:], opts = [:]

    model.parameters.each { p ->
        ( p.required ? params : opts )[p.name] = objectTypeToMap(fetchActualClass(p.rawType))
    }


    boolean requiresNode = step.requiredContext.contains(FilePath)
    boolean takesClosure = step.takesImplicitBlockArgument()
    String sanitizedDisplayName = StringEscapeUtils.escapeJavaScript(step.displayName)
    String description = sanitizedDisplayName
    if (step.isAdvanced()) {
        description = "Advanced/Deprecated " + description
    }

    if (params.size() <= 1) {
        def fixedParams = params

        if (takesClosure) {
            fixedParams = params + ['body': "Closure"]
        }
        String contr = "method(name: '${step.functionName}', type: 'Object', useNamedArgs: false, params: ${fixedParams}, doc: '${description}')"
        if (requiresNode) {
            nodeContext.add(contr)
        } else {
            scriptContext.add(contr)
        }
    }
    if (!opts.isEmpty() || params.size() > 1) {
        def paramsMap = [:]
        if (takesClosure) {
            paramsMap.put('body', 'Closure')
        }

        for (def p : params) {
            paramsMap.put(p.key, p.value)
        }
        for (def p : opts) {
            paramsMap.put(p.key, p.value)
        }
        String contr = "method(name: '${step.functionName}', type: 'Object', useNamedArgs: true, params: ${paramsMap}, doc: '${sanitizedDisplayName}')"

        if (requiresNode) {
            nodeContext.add(contr)
        } else {
            scriptContext.add(contr)
        }
    }
}

def globalVars = []

for (GlobalVariable v : snippetizer.getGlobalVariables()) {
    globalVars.add("property(name: '${v.getName()}', type: '${v.class.canonicalName}')")
}

def st = namespace("jelly:stapler")
st.contentType(value: "text/plain;charset=UTF-8")

raw("""
def insideNode = { enclosingCallName("node") & inClosure() }

(!insideNode() && currentType(subType(Script))).accept {
${scriptContext.join('\n')}
${globalVars.join('\n')}
}

//Steps that require a node context
insideNode().accept {
${nodeContext.join('\n')}
}

// Errors on:
${errorSteps.collect { k, v ->
    "// ${k}: ${v}"
}.join("\n")}
""")

// Render anything other than a primitive, Closure, or a java.* class as a Map.
def objectTypeToMap(String type) {
    if (type != null && type.contains(".") && !(type.startsWith("java"))) {
        return "Map"
    } else {
        return type
    }
}

def fetchActualClass(Type type) {
    if (type == null) {
        return "null"
    } else if (type instanceof Class) {
        return ((Class<?>) type).name
    } else if (type instanceof ParameterizedType) {
        return ((ParameterizedType) type).getRawType().toString()
    } else {
        return type.toString()
    }
}
