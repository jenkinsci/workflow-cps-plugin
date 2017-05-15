
package com.cloudbees.groovy.cps;

import javax.annotation.Generated;
import com.cloudbees.groovy.cps.impl.Caller;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.ProcessGroovyMethods;

@Generated(value = "com.cloudbees.groovy.cps.tool.Translator", date = "Mon May 15 17:17:37 EDT 2017", comments = "based on groovy-2.4.7-sources.jar")
public class CpsProcessGroovyMethods {


    public static void withWriter(Process self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "withWriter", closure))&&(!Caller.isAsynchronous(CpsProcessGroovyMethods.class, "withWriter"))) {
            ProcessGroovyMethods.withWriter(self, closure);
            return ;
        }
        throw new UnsupportedOperationException("org.codehaus.groovy.runtime.ProcessGroovyMethods.withWriter(java.lang.Process,groovy.lang.Closure) is not yet supported for translation; use another idiom, or wrap in @NonCPS");
    }

    public static void withOutputStream(Process self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "withOutputStream", closure))&&(!Caller.isAsynchronous(CpsProcessGroovyMethods.class, "withOutputStream"))) {
            ProcessGroovyMethods.withOutputStream(self, closure);
            return ;
        }
        throw new UnsupportedOperationException("org.codehaus.groovy.runtime.ProcessGroovyMethods.withOutputStream(java.lang.Process,groovy.lang.Closure) is not yet supported for translation; use another idiom, or wrap in @NonCPS");
    }

    private static MethodLocation loc(String methodName) {
        return new MethodLocation(CpsProcessGroovyMethods.class, methodName);
    }

}
