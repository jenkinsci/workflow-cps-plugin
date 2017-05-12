
/*
 * The MIT License
 *
 * Copyright (c) 2017, CloudBees, Inc.
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
package org.jenkinsci.plugins.workflow.cps.actions;

import hudson.EnvVars;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.jenkinsci.plugins.workflow.actions.ArgumentsAction.NotStoredReason.MASKED_VALUE;
import static org.jenkinsci.plugins.workflow.actions.ArgumentsAction.NotStoredReason.OVERSIZE_VALUE;

/**
 * Implements {@link ArgumentsAction} by storing step arguments, with sanitization.
 */
@Restricted(NoExternalUse.class)
public class ArgumentsActionImpl extends ArgumentsAction {

    /** Arguments to the step, for cases where we cannot simply store the step because masking was applied */
    @CheckForNull
    private Map<String,Object> arguments;


    private boolean isUnmodifiedBySanitization = true;

    public ArgumentsActionImpl(@Nonnull Map<String, Object> stepArguments, @Nullable EnvVars env) {
        Map<String,Object> sanitizedArguments = sanitizedStepArguments(stepArguments, env);
        for (Object o : sanitizedArguments.values()) {
            if (o != null && (o.equals(MASKED_VALUE) || o.equals(NotStoredReason.OVERSIZE_VALUE))) {
                isUnmodifiedBySanitization = false;
                break;
            }
        }
        arguments = sanitizedArguments;
    }

    /** Create a step, sanitizing strings for secured content */
    public ArgumentsActionImpl(@Nonnull Map<String, Object> stepArguments) {
        this(stepArguments, new EnvVars());
    }

    /** See if sensitive environment variable content is in a string */
    public static boolean isStringSafe(@CheckForNull String input, @CheckForNull EnvVars variables, @Nonnull Set<String> safeEnvVariables) {
        if (input == null || variables == null || variables.size() == 0) {
            return true;
        }
        StringBuilder pattern = new StringBuilder();
        int count = 0;
        for (Map.Entry<String,String> ent : variables.entrySet()) {
            String val = ent.getValue();
            if (val == null || val.isEmpty() || safeEnvVariables.contains(ent.getKey())) {  // Skip values that are safe
                continue;
            }
            if (count > 0) {
                pattern.append('|');
            }
            pattern.append(Pattern.quote(val));
            count++;
        }
        return (count > 0)
                ? !Pattern.compile(pattern.toString()).matcher(input).find()
                : true;
    }

    /** Normal environment variables, as opposed to ones that might come from credentials bindings */
    private static final HashSet<String> SAFE_ENVIRONMENT_VARIABLES = new HashSet<String>(Arrays.asList(
            // Pipeline/Jenkins variables in normal builds
            "BRANCH_NAME",
            "BUILD_DISPLAY_NAME",
            "BUILD_ID",
            "BUILD_NUMBER",
            "BUILD_TAG",
            "BUILD_URL",
            "CHANGE_AUTHOR",
            "CHANGE_AUTHOR_DISPLAY_NAME",
            "CHANGE_AUTHOR_EMAIL",
            "CHANGE_ID",
            "CHANGE_TARGET",
            "CHANGE_TITLE",
            "CHANGE_URL",
            "EXECUTOR_NUMBER",
            "HUDSON_COOKIE",
            "HUDSON_HOME",
            "HUDSON_SERVER_COOKIE",
            "HUDSON_URL",
            "JENKINS_HOME",
            "JENKINS_SERVER_COOKIE",
            "JENKINS_URL",
            "JOB_BASE_NAME",
            "JOB_NAME",
            "JOB_URL",
            "NODE_LABELS",
            "NODE_NAME",
            "WORKSPACE",

            // Normal system variables for posix environments
            "HOME",
            "LANG",
            "LOGNAME",
            "MAIL",
            "NLSPATH",
            "PATH",
            "PWD",
            "SHELL",
            "SHLVL",
            "TERM",
            "USER",
            "XFILESEARCHPATH"
    ));

    /**
     * Does first-level sanitization of a step's params, returning the Describable arguments map
     * @param stepArguments Arguments provided when creating the step
     * @param variables Environment variables to remove
     * @return
     */
    @Nonnull
    public static Map<String,Object> sanitizedStepArguments(@Nonnull Map<String, Object> stepArguments, @CheckForNull EnvVars variables) {
        HashMap<String, Object> output = new HashMap<String, Object>();

        if (variables == null || variables.size() == 0) {
            // No need to sanitize against environment variables
            for (Map.Entry<String,Object> entry : stepArguments.entrySet()) {
                output.put(entry.getKey(), ArgumentsAction.isOversized(entry.getValue()) ? OVERSIZE_VALUE : entry.getValue());
            }
            return output;
        }

        for (Map.Entry<String,?> param : stepArguments.entrySet()) {
            Object val = param.getValue();
            if (ArgumentsAction.isOversized(val)) {
                output.put(param.getKey(), OVERSIZE_VALUE);
            } else if (val instanceof String && !isStringSafe((String)val, variables, SAFE_ENVIRONMENT_VARIABLES)) {
                output.put(param.getKey(), MASKED_VALUE);
            } else {
                output.put(param.getKey(), val);
            }
        }
        return output;
    }

    @Nonnull
    @Override
    protected Map<String, Object> getArgumentsInternal() {
        return (arguments == null)? Collections.EMPTY_MAP : arguments;
    }

    @Override
    public boolean isUnmodifiedArguments() {
        return isUnmodifiedBySanitization;
    }
}
