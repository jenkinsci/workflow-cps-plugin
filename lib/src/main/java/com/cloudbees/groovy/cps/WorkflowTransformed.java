package com.cloudbees.groovy.cps;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used internally to designate methods that were actually CPS-transformed.
 *
 * This helps us detect irregular situations like failing to transform a method
 * or attempt to double-transform methods.
 *
 * @author Kohsuke Kawaguchi
 */
@Target({METHOD, TYPE})
@Retention(RUNTIME)
public @interface WorkflowTransformed {}
