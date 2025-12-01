/*
 * The MIT License
 *
 * Copyright 2016 CloudBees, Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.jenkinsci.plugins.workflow.cps;

import hudson.Extension;
import hudson.model.ParameterDefinition;
import hudson.model.ParameterValue;
import hudson.model.ParametersAction;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Run;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.jenkinsci.plugins.workflow.pickles.PickleFactory;

/**
 * Allows access to {@link ParametersAction}.
 */
@Extension
public class ParamsVariable extends GlobalVariable {

    @Override
    public String getName() {
        return "params";
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object getValue(CpsScript script) throws Exception {
        Run<?, ?> b = script.$build();
        if (b == null) {
            throw new IllegalStateException("cannot find owning build");
        }
        // Could extend AbstractMap and make a Serializable lazy wrapper, but getValue impls seem cheap anyway.
        Map<String, Object> values = new HashMap<>();
        ParametersAction action = b.getAction(ParametersAction.class);
        if (action != null) {
            for (ParameterValue parameterValue : action.getAllParameters()) {
                addValue(values, parameterValue);
            }
        }
        ParametersDefinitionProperty prop = b.getParent().getProperty(ParametersDefinitionProperty.class);
        if (prop != null) { // JENKINS-35698: look for default values as well
            for (ParameterDefinition param : prop.getParameterDefinitions()) {
                if (!values.containsKey(param.getName())) {
                    ParameterValue defaultParameterValue = param.getDefaultParameterValue();
                    if (defaultParameterValue != null) {
                        addValue(values, defaultParameterValue);
                    }
                }
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private static void addValue(Map<String, Object> values, ParameterValue parameterValue) {
        Object value = parameterValue.getValue();
        if (value != null && !(value instanceof Serializable)) {
            boolean canPickle = false;
            for (PickleFactory pf : PickleFactory.all()) {
                if (pf.writeReplace(value) != null) {
                    // For example SecretPickle can handle Secret from PasswordParameterValue.
                    canPickle = true;
                    break;
                }
            }
            if (!canPickle) {
                // Just skip anything not serializable, such as a Run from a RunParameterValue.
                return;
            }
        }
        values.put(parameterValue.getName(), value);
    }
}
