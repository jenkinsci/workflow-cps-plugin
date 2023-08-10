package org.jenkinsci.plugins.workflow.cps.Snippetizer

import groovy.json.StringEscapeUtils
import hudson.FilePath
import hudson.Functions
import org.jenkinsci.plugins.structs.describable.DescribableModel
import org.jenkinsci.plugins.workflow.cps.GlobalVariable
import org.jenkinsci.plugins.workflow.cps.Snippetizer
import org.jenkinsci.plugins.workflow.cps.steps.ParallelStep
import org.jenkinsci.plugins.workflow.steps.Step
import org.jenkinsci.plugins.workflow.steps.StepDescriptor

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import jenkins.model.Jenkins

Snippetizer snippetizer = my;

Map<StepDescriptor,DescribableModel> steps = new LinkedHashMap<StepDescriptor, DescribableModel>();

def errorSteps = [:]
def nodeContext = []
def scriptContext = []

[false, true].each { isAdvanced ->
    snippetizer.getQuasiDescriptors(isAdvanced).each { d ->
        if (!Step.isAssignableFrom(d.real.clazz)) {
            return // TODO JENKINS-37215
        }
        StepDescriptor step = Jenkins.get().getDescriptor(d.real.clazz)
        if (step instanceof ParallelStep.DescriptorImpl) {
            // ParallelStep does not support data binding, so we just hard-code the correct entries.
            scriptContext.add("method(name: 'parallel', type: 'Object', params: ['closures':'java.util.Map'], doc: 'Execute in parallel')")
            scriptContext.add("method(name: 'parallel', type: 'Object', namedParams: [parameter(name: 'closures', type: 'java.util.Map'), parameter(name: 'failFast', type: 'boolean'), ], doc: 'Execute in parallel')")
            return
        }
        DescribableModel schema
        try {
            schema = new DescribableModel(d.real.clazz)
        } catch (Exception e) {
            println "Error on ${d.real.clazz}: ${Functions.printThrowable(e)}"
            errorSteps.put(d.real.clazz, e.message)
        }
        if (schema != null) {
            steps.put(step, schema)
        }

    }
}

steps.each { StepDescriptor step, DescribableModel model ->
    def params = [:], opts = [:]

    model.parameters.each { p ->
        ( p.required ? params : opts )[p.name] = objectTypeToMap(fetchActualClass(p.rawType))
    }

    boolean requiresNode = step.requiredContext.contains(FilePath)
    boolean takesClosure = step.takesImplicitBlockArgument()
    def sanitizedDisplayName = StringEscapeUtils.escapeJava(step.displayName).replace('\'', '\\\'')
    String description = sanitizedDisplayName
    if (step.isAdvanced()) {
        description = "Advanced/Deprecated " + description
    }

    if (params.size() <= 1) {
        def fixedParams = params.collectEntries { k, v ->
            [k, "'${v}'"]
        }
        if (takesClosure) {
            fixedParams = params + ['body': "'Closure'"]
        }
        String contr = "method(name: '${step.functionName}', type: 'Object', params: ${fixedParams}, doc: '${description}')"
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
        StringBuilder namedParamsS = new StringBuilder()
        for (def p : params) {
            namedParamsS.append("parameter(name: '${p.key}', type: '${p.value}'), ")
        }
        for (def p : opts) {
            namedParamsS.append("parameter(name: '${p.key}', type: '${p.value}'), ")
        }
        String contr
        if (takesClosure) {
            contr = "method(name: '${step.functionName}', type: 'Object', params: [body:Closure], namedParams: [${namedParamsS.toString()}], doc: '${sanitizedDisplayName}')"
        } else {
            contr = "method(name: '${step.functionName}', type: 'Object', namedParams: [${namedParamsS.toString()}], doc: '${sanitizedDisplayName}')"
        }
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
//The global script scope
def ctx = context(scope: scriptScope())
contributor(ctx) {
${scriptContext.join('\n')}
${globalVars.join('\n')}
}
//Steps that require a node context
def nodeCtx = context(scope: closureScope())
contributor(nodeCtx) {
    def call = enclosingCall('node')
    if (call) {
${nodeContext.join('\n')}
    }
}

// Errors on:
${errorSteps.collect { k, v ->
    "// ${k}: ${v}"
}.join("\n")}
""")

// Render anything other than a primitive, Closure, or a java.* class or interface as a Map.
def objectTypeToMap(String type) {
    if (type != null && type.contains(".")) {
        if (type.startsWith("interface ")) {
            type = type.substring("interface ".length());
        }
        if (!type.startsWith("java")) {
            return "Map"
        }
    }
    return type
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
