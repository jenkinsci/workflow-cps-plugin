package com.cloudbees.groovy.cps;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Exclude a marked method from CPS transformation.
 *
 * Useful for performance sensitive code or where you need to call into libraries and pass in closures
 * that cannot be CPS-transformed.
 *
 * @author Kohsuke Kawaguchi
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface NonCPS {}
