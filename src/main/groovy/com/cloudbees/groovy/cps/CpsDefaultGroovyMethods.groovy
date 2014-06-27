package com.cloudbees.groovy.cps;

import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.transform.GroovyASTTransformation;

import java.util.Iterator;
import java.util.Map;

/**
 * @author Kohsuke Kawaguchi
 */
@GroovyASTTransformation
public class CpsDefaultGroovyMethods {
    /**
     * Interception is successful. The trick is to pre-translate this method into CPS.
     */
    public static <T> T each(T self, Closure closure) {
        each(InvokerHelper.asIterator(self), closure);
        return self;
    }

    public static <T> Iterator<T> each(Iterator<T> iter, Closure closure) {
        while (iter.hasNext()) {
            Object arg = iter.next();
            closure.call(arg);
        }
        return iter;
    }
}
