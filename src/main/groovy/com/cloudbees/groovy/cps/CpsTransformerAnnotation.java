package com.cloudbees.groovy.cps;

import org.codehaus.groovy.transform.GroovyASTTransformationClass;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Kohsuke Kawaguchi
 */
@Documented
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.TYPE})
@GroovyASTTransformationClass(classes=CpsTransformingASTCustomizer.class)
public @interface CpsTransformerAnnotation {
}

