package com.cloudbees.groovy.cps;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * Designates a method that shall be CPS-transformed.
 *
 * @author Kohsuke Kawaguchi
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface WorkflowMethod {
}
