
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

import com.google.common.collect.Maps;
import groovy.lang.GroovyClassLoader;
import hudson.EnvVars;
import hudson.model.Describable;
import hudson.model.Result;
import java.util.Collection;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.structs.describable.DescribableModel;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.jenkinsci.plugins.workflow.actions.ArgumentsAction;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Implements {@link ArgumentsAction} by storing step arguments, with sanitization.
 */
@Restricted(NoExternalUse.class)
public class ArgumentsActionImpl extends ArgumentsAction {

    /** Arguments to the step, for cases where we cannot simply store the step because masking was applied */
    @CheckForNull
    private Map<String,Object> arguments;

    private final Set<String> sensitiveVariables;

    boolean isUnmodifiedBySanitization = true;

    private static final Logger LOGGER = Logger.getLogger(ArgumentsActionImpl.class.getName());

    public ArgumentsActionImpl(@NonNull Map<String, Object> stepArguments, @CheckForNull EnvVars env, @NonNull Set<String> sensitiveVariables) {
        this.sensitiveVariables = new HashSet<>(sensitiveVariables);
        this.arguments = serializationCheck(sanitizeStepArguments(stepArguments, env));
    }

    /** Create a step, sanitizing strings for secured content */
    public ArgumentsActionImpl(@NonNull Map<String, Object> stepArguments) {
        this(stepArguments, new EnvVars(), Collections.emptySet());
    }

    /** For testing use only */
    ArgumentsActionImpl(@NonNull Set<String> sensitiveVariables){
        this.isUnmodifiedBySanitization = true;
        this.arguments = Collections.emptyMap();
        this.sensitiveVariables = sensitiveVariables;
    }

    /** See if sensitive environment variable content is in a string and replace the content with its associated variable name, otherwise return string unmodified*/
    public static String replaceSensitiveVariables(@NonNull String input, @CheckForNull EnvVars variables, @NonNull Set<String> sensitiveVariables) {
        if (variables == null || variables.size() == 0 || sensitiveVariables.size() ==0) {
            return input;
        }
        String modded = input;
        for (String sensitive : sensitiveVariables) {
            String sensitiveValue = variables.get(sensitive, "");
            if (sensitiveValue.isEmpty()) {
                continue;
            }
            modded = modded.replace(variables.get(sensitive), "${" + sensitive + "}");
        }
        return modded;
    }

    /** Restrict stored arguments to a reasonable subset of types so we don't retain totally arbitrary objects
     *  in memory. Generally we aim to allow storing anything that maps correctly to an {@link UninstantiatedDescribable}
     *  argument type, but we may allow a few extras it doesn't address, if they're safe & easy to store to store.
     *
     *  See {@link DescribableModel}, and specifically note that many types are handled via {@link DescribableModel#coerce(String, Type, Object)}
     *  to create more advanced types (i.e. Result, URL, etc) from Strings or simple types. For convenience and to ensure
     *  we can deal with idiosyncratic or legacy syntaxes, we store original or partially-processed forms if viable.
     *
     *  Note also that Map is reserved for the arguments derived from exploded {@link Describable} instances, and Lists/Arrays may have a coercion applied and are supposed
     *  to just be collections of Describables. We pass these through because they're used in parts of the recursive sanitization routines, and are themselves recursively
     *  filtered.
     */
    boolean isStorableType(Object ob) {
        if (ob == null) {
            return true;
        } else if (ob instanceof CharSequence || ob instanceof Number || ob instanceof Boolean
                || ob instanceof Map || ob instanceof List || ob instanceof UninstantiatedDescribable
                || ob instanceof URL || ob instanceof Result) {
            return true;
        }
        if (ob instanceof Enum) {
            // We use getDeclaringClass instead of getClass to handle enums with value-specific class bodies like TimeUnit.
            Class enumClass = ((Enum) ob).getDeclaringClass();
            return !(enumClass.getClassLoader() instanceof GroovyClassLoader);
        }
        Class c = ob.getClass();
        return c.isPrimitive() || (c.isArray() && !(c.getComponentType().isPrimitive()));  // Primitive arrays are not legal here
    }

    /**
     * Sanitize a list recursively
     */
    @CheckForNull
    Object sanitizeListAndRecordMutation(@NonNull List objects, @CheckForNull EnvVars variables) {
        // Package scoped so we can test it directly

        if (isOversized(objects)) {
            this.isUnmodifiedBySanitization = false;
            return NotStoredReason.OVERSIZE_VALUE;
        }

        boolean isMutated = false;
        List output = new ArrayList(objects.size());
        long size = objects.size();
        for (Object o : objects) {
            Object modded = sanitizeObjectAndRecordMutation(o, variables);
            size += shallowSize(modded);
            if (size > MAX_RETAINED_LENGTH) {
                this.isUnmodifiedBySanitization = false;
                return NotStoredReason.OVERSIZE_VALUE;
            }
            if (modded != o) {
                // Sanitization stripped out some values, so we need to store the mutated object
                output.add(modded);
                isMutated = true; //isUnmodifiedBySanitization was already set
            } else { // Any mutation was just from exploding step/uninstantiated describable, and we can just use the original
                output.add(o);
            }
        }

        return (isMutated) ? output : objects; // Throw away copies and use originals wherever possible
    }

    /** For object arrays, we sanitize recursively, as with Lists */
    @CheckForNull
    Object sanitizeArrayAndRecordMutation(@NonNull Object[] objects, @CheckForNull EnvVars variables) {
        if (isOversized(objects)) {
            this.isUnmodifiedBySanitization = false;
            return NotStoredReason.OVERSIZE_VALUE;
        }
        List<Object> inputList = Arrays.asList(objects);
        Object sanitized = sanitizeListAndRecordMutation(inputList, variables);
        if (sanitized == inputList) { // Works because if not mutated, we return original input instance
            return objects;
        } else if (sanitized instanceof List) {
            return ((List) sanitized).toArray();
        } else { // Enum or null or whatever.
            return sanitized;
        }
    }

    /** Recursively sanitize a single object by:
     *   - Exploding {@link Step}s and {@link UninstantiatedDescribable}s into their Maps to sanitize
     *   - Removing unsafe strings using {@link #replaceSensitiveVariables(String, EnvVars, Set)} and replace with the variable name
     *   - Removing oversized objects using {@link #isOversized(Object)} and replacing with {@link NotStoredReason#OVERSIZE_VALUE}
     *  While making an effort not to retain needless copies of objects and to re-use originals where possible
     *   (including the Step or UninstantiatedDescribable)
     */
    @CheckForNull
    @SuppressWarnings("unchecked")
    Object sanitizeObjectAndRecordMutation(@CheckForNull Object o, @CheckForNull EnvVars vars) {
        // Package scoped so we can test it directly
        Object tempVal = o;
        DescribableModel m = null;
        if (tempVal instanceof Step) {
            // Ugly but functional used for legacy syntaxes with metasteps
            m = DescribableModel.of(tempVal.getClass());
            tempVal = ((Step)tempVal).getDescriptor().defineArguments((Step)tempVal);
        } else if (tempVal instanceof UninstantiatedDescribable) {
            tempVal = ((UninstantiatedDescribable)tempVal).toMap();
        } else if (tempVal instanceof Describable) {  // Raw Describables may not be safe to store, so we should explode it
            try {
                m = DescribableModel.of(tempVal.getClass());
                tempVal = m.uninstantiate2(o).toMap();
            } catch (RuntimeException x) { // usually NoStaplerConstructorException, but could also be misc. UnsupportedOperationException
                LOGGER.log(Level.FINE, "failed to store " + tempVal, x);
                this.isUnmodifiedBySanitization = false;
                return NotStoredReason.UNSERIALIZABLE;
            }
        }

        if (isOversized(tempVal)) {
            this.isUnmodifiedBySanitization = false;
            return NotStoredReason.OVERSIZE_VALUE;
        }

        if (!isStorableType(tempVal)) {  // If we're not a legal type to store, then don't.
            this.isUnmodifiedBySanitization = false;
            return NotStoredReason.UNSERIALIZABLE;
        }

        Object modded = tempVal;
        if (modded instanceof Map) {
            // Recursive sanitization, oh my!
            modded = sanitizeMapAndRecordMutation((Map)modded, vars, false);
        } else if (modded instanceof List) {
            modded = sanitizeListAndRecordMutation((List) modded, vars);
        } else if (modded != null && modded.getClass().isArray()) {
            Class componentType = modded.getClass().getComponentType();
            if (!componentType.isPrimitive()) {  // Object arrays get recursively sanitized
                modded = sanitizeArrayAndRecordMutation((Object[])modded, vars);
            } else {  // Primitive arrays aren't a valid type here
                this.isUnmodifiedBySanitization = true;
                return NotStoredReason.UNSERIALIZABLE;
            }
        } else if (modded instanceof String && ((String) modded).contains("\0")) {
            this.isUnmodifiedBySanitization = false;
            return "<contains ASCII NUL>";
        } else if (modded instanceof String && vars != null && !vars.isEmpty()) {
            String replaced = replaceSensitiveVariables((String)modded, vars, sensitiveVariables);
            if (!replaced.equals(modded)) {
                this.isUnmodifiedBySanitization = false;
            }
            return replaced;
        }

        if (modded != tempVal) {
            // Sanitization stripped out some values, so we need to record that and return modified version
            this.isUnmodifiedBySanitization = false;
            if (o instanceof Describable && !(o instanceof Step)) { // Return an UninstantiatedDescribable for the input Describable with masking applied to arguments
                // We're skipping steps because for those we want to return the raw arguments anyway...
                UninstantiatedDescribable rawUd = m.uninstantiate2(o);
                return new UninstantiatedDescribable(rawUd.getSymbol(), rawUd.getKlass(), (Map<String, ?>) modded);
            } else if (o instanceof UninstantiatedDescribable) {
                // Need to retain the symbol.
                UninstantiatedDescribable ud = (UninstantiatedDescribable) o;
                return new UninstantiatedDescribable(ud.getSymbol(), ud.getKlass(), (Map<String, ?>) modded);
            } else {
                return modded;
            }
        } else if (o instanceof Describable && tempVal instanceof Map) {  // Handle oddball cases where Describable is passed in directly and we need to uninstantiate.
            UninstantiatedDescribable rawUd = m.uninstantiate2(o);
            return rawUd;
        } else {  // Any mutation was just from exploding step/uninstantiated describable, and we can just use the original
            return o;
        }
    }

    /** Verify that all the arguments WILL serialize and if not replace with {@link org.jenkinsci.plugins.workflow.actions.ArgumentsAction.NotStoredReason#UNSERIALIZABLE}
     *  See JENKINS-50752 for details, but the gist is we need to avoid problems before physical persistence to prevent data loss.
     *  @return Arguments
     */
    Map<String, Object> serializationCheck(@NonNull Map<String, Object> arguments) {
        boolean isMutated = false;
        HashMap<String, Object> out = Maps.newHashMapWithExpectedSize(arguments.size());
        for (Map.Entry<String, Object> entry : arguments.entrySet()) {
            Object val = entry.getValue();
            try {
                if (val != null && !(val instanceof String) && !(val instanceof Boolean) && !(val instanceof Number) && !(val instanceof NotStoredReason) && !(val instanceof TimeUnit)) {
                    // We only need to check serialization for nontrivial types
                    Jenkins.XSTREAM2.toXMLUTF8(entry.getValue(), OutputStream.nullOutputStream());  // Hacky but can't find a better way
                }
                out.put(entry.getKey(), entry.getValue());
            } catch (Exception ex) {
                out.put(entry.getKey(), NotStoredReason.UNSERIALIZABLE);
                isMutated = true;
                LOGGER.log(Level.FINE, "Failed to serialize argument "+entry.getKey(), ex);
            }
        }
        if (isMutated) {
            this.isUnmodifiedBySanitization = false;
        }
        return out;
    }

    /**
     * Goes through {@link #sanitizeObjectAndRecordMutation(Object, EnvVars)} for each value in a map input.
     */
    @NonNull
    Object sanitizeMapAndRecordMutation(@NonNull Map<String, Object> mapContents, @CheckForNull EnvVars variables) {
        // Package scoped so we can test it directly
        return sanitizeMapAndRecordMutation(mapContents, variables, false);
    }

    private Map<String, Object> sanitizeStepArguments(Map<String, Object> stepArguments, EnvVars env) {
        // Guaranteed to be a map, the block returning something else is guarded by topLevel == false
        return (Map<String, Object>) sanitizeMapAndRecordMutation(stepArguments, env, true);
    }

    /**
     * Goes through {@link #sanitizeObjectAndRecordMutation(Object, EnvVars)} for each value in a map input.
     */
    @NonNull
    private Object sanitizeMapAndRecordMutation(@NonNull Map<String, Object> mapContents, @CheckForNull EnvVars variables, boolean topLevel) {
        LinkedHashMap<String, Object> output = new LinkedHashMap<>(mapContents.size());
        long size = mapContents.size();

        boolean isMutated = false;
        for (Map.Entry<String,?> param : mapContents.entrySet()) {
            Object modded = sanitizeObjectAndRecordMutation(param.getValue(), variables);
            if (!topLevel) {
                size += param.getKey().length();
                size += shallowSize(modded);
                if (size > MAX_RETAINED_LENGTH) {
                    this.isUnmodifiedBySanitization = false;
                    return NotStoredReason.OVERSIZE_VALUE;
                }
            }
            if (modded != param.getValue()) {
                // Sanitization stripped out some values, so we need to store the mutated object
                output.put(param.getKey(), modded);
                isMutated = true; //isUnmodifiedBySanitization was already set
            } else { // Any mutation was just from exploding step/uninstantiated describable, and we can just use the original
                output.put(param.getKey(), param.getValue());
            }
        }

        return isMutated ? output : mapContents;
    }

    static long shallowSize(Object o) {
        if (o == null) {
            return 0;
        }
        if (o instanceof CharSequence) {
            return ((CharSequence) o).length();
        }
        if (o instanceof Collection) {
            Collection<?> collection = (Collection<?>) o;
            return collection.size();
        }
        if (o.getClass().isArray()) {
            Object[] array = (Object[]) o;
            return array.length;
        }
        if (o instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) o;
            return map.size();
        }
        return 1;
    }

    /** Accessor for testing use */
    static int getMaxRetainedLength() {
        return MAX_RETAINED_LENGTH;
    }

    @NonNull
    @Override
    protected Map<String, Object> getArgumentsInternal() {
        return arguments == null ? Collections.EMPTY_MAP : arguments;
    }

    @Override
    public boolean isUnmodifiedArguments() {
        return isUnmodifiedBySanitization;
    }
}
