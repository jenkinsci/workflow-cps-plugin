
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
import hudson.model.Describable;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
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
        arguments = takeSanitizedMap(stepArguments, env);
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
            "XFILESEARCHPATH",

            // Windows system variables
            "ALLUSERSPROFILE",
            "APPDATA",
            "CD",
            "ClientName",
            "CMDEXTVERSION",
            "CMDCMDLINE",
            "CommonProgramFiles",
            "COMPUTERNAME",
            "COMSPEC",
            "DATE",
            "ERRORLEVEL",
            "HighestNumaNodeNumber",
            "HOMEDRIVE",
            "HOMEPATH",
            "LOCALAPPDATA",
            "LOGONSERVER",
            "NUMBER_OF_PROCESSORS",
            "OS",
            "PATHEXT",
            "PROCESSOR_ARCHITECTURE",
            "PROCESSOR_ARCHITEW6432",
            "PROCESSOR_IDENTIFIER",
            "PROCESSOR_LEVEL",
            "PROCESSOR_REVISION",
            "ProgramW6432",
            "ProgramData",
            "ProgramFiles",
            "ProgramFiles (x86)",
            "PROMPT",
            "PSModulePath",
            "Public",
            "RANDOM",
            "%SessionName%",
            "SYSTEMDRIVE",
            "SYSTEMROOT",
            "TEMP", "TMP",
            "TIME",
            "UserDnsDomain",
            "USERDOMAIN",
            "USERDOMAIN_roamingprofile",
//            "USERNAME",  // Not whitelisted because this is a likely variable name for credentials binding
            "USERPROFILE",
            "WINDIR"
    ));

    /**
     * Sanitize a list recursively,
     */
    private Object takeSanitizedList(@Nonnull List objects, @CheckForNull EnvVars variables) {

        if (isOversized(objects)) {
            this.isUnmodifiedBySanitization = false;
            return NotStoredReason.OVERSIZE_VALUE;
        }

        boolean isMutated = false;
        List output = new ArrayList(objects.size());
        for (Object o : objects) {
            Object tempVal = o;

            // Need to explode these objects into maps for sanitization, to inspect contents
            if (tempVal instanceof Step) {
                tempVal = ((Step)tempVal).getDescriptor().defineArguments((Step)tempVal);
            } else if (tempVal instanceof UninstantiatedDescribable) {
                tempVal = ((UninstantiatedDescribable)tempVal).toMap();
            }

            if (isOversized(tempVal)) {
                this.isUnmodifiedBySanitization = false;
                isMutated = true;
                output.add(NotStoredReason.OVERSIZE_VALUE);
                continue;
            }

            Object modded = tempVal;
            // Separate tempVal from modded so we only return a different object if sanitization changed it
            // Rather than just exploding the Step/Describable
            if (tempVal instanceof Map) {
                modded = takeSanitizedMap((Map)tempVal, variables);
            } else if (tempVal instanceof List) {
                modded = takeSanitizedList((List)tempVal, variables);
            } else {
                modded = sanitizeSingleton(tempVal, variables);
            }

            if (modded != tempVal) {
                // Sanitization stripped out some values, so we need to store the mutated object
                output.add(modded);
                isMutated = true;
                isUnmodifiedBySanitization = false;
            } else { // Any mutation was just from exploding step/uninstantiated describable, and we can just use the original
                output.add(o);
            }
        }

        return (isMutated) ? output : objects; // Throw away copies and use originals wherever possible
    }

    /** Sanitize a single non-Map, non-List, non-{@link Step}/non-{@link UninstantiatedDescribable} item */
    private Object sanitizeSingleton(@CheckForNull Object o, @CheckForNull EnvVars vars) {
        if (isOversized(o)) {
            this.isUnmodifiedBySanitization = false;
            return NotStoredReason.OVERSIZE_VALUE;
        }

        if (o instanceof String && vars != null && !vars.isEmpty() && !isStringSafe((String)o, vars, SAFE_ENVIRONMENT_VARIABLES)) {
            this.isUnmodifiedBySanitization = false;
            return NotStoredReason.MASKED_VALUE;
        }
        return o;
    }

    /**
     * Does sanitization of a step's params, returning the Describable arguments map,
     * as from {@link org.jenkinsci.plugins.workflow.steps.StepDescriptor#defineArguments(Step)}
     * Returns original input if not modified.
     * Due to restrictions in {@link org.jenkinsci.plugins.structs.describable.DescribableModel#instantiate(Map)}
     *  we do not need to sanitize keys, which can only parameters to a {@link Describable}
     * @param stepArguments Arguments provided when creating the step
     * @param variables Environment variables to remove
     * @return Arguments map, with oversized values and values containing bound credentials stripped out.
     */
    @Nonnull
    private  Map<String,Object> takeSanitizedMap(@Nonnull Map<String, Object> stepArguments, @CheckForNull EnvVars variables) {
        HashMap<String, Object> output = new HashMap<String, Object>();

        boolean isMutated = false;
        for (Map.Entry<String,?> param : stepArguments.entrySet()) {
            Object tempVal = param.getValue();
            if (tempVal instanceof Step) {
                // Ugly but functional used for legacy syntaxes with metasteps
                tempVal = ((Step)tempVal).getDescriptor().defineArguments((Step)tempVal);
            } else if (tempVal instanceof UninstantiatedDescribable) {
                tempVal = ((UninstantiatedDescribable)tempVal).toMap();
            }

            if (ArgumentsAction.isOversized(tempVal)) {
                isMutated = true;
                this.isUnmodifiedBySanitization = false;
                output.put(param.getKey(), OVERSIZE_VALUE);
                continue;
            }

            Object modded = tempVal;
            if (modded instanceof Map) {
                // Recursive sanitization, oh my!
                Map<String,Object> mapFormat = (Map<String,Object>)tempVal;
                Map<String,Object> newVal = takeSanitizedMap(mapFormat, variables);
                isMutated |= (newVal != tempVal); // If we got back a new object, something was modified
            } else if (modded instanceof List) {
                modded = takeSanitizedList((List) modded, variables);
            } else {
                modded = sanitizeSingleton(modded, variables);
            }

            if (modded != tempVal) {
                // Sanitization stripped out some values, so we need to store the mutated object
                output.put(param.getKey(), modded);
                isMutated = true;
                isUnmodifiedBySanitization = false;
            } else { // Any mutation was just from exploding step/uninstantiated describable, and we can just use the original
                output.put(param.getKey(), param.getValue());
            }
        }

        return (isMutated) ? output : stepArguments;
    }

    @Nonnull
    @Override
    protected Map<String, Object> getArgumentsInternal() {
        return arguments == null ? Collections.EMPTY_MAP : arguments;
    }

    @Override
    public boolean isUnmodifiedArguments() {
        return isUnmodifiedBySanitization;
    }
}
