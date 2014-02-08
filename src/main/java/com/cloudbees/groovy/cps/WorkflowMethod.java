package com.cloudbees.groovy.cps;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.*;

/**
 * @author Kohsuke Kawaguchi
 */
@Target(METHOD)
@Retention(RUNTIME)
public @interface WorkflowMethod {
}
