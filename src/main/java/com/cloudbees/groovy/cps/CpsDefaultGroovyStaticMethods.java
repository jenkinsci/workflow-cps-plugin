
package com.cloudbees.groovy.cps;

import java.util.Arrays;
import javax.annotation.Generated;
import com.cloudbees.groovy.cps.impl.Caller;
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.CpsFunction;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyStaticMethods;

@Generated(value = "com.cloudbees.groovy.cps.tool.Translator", date = "Fri May 12 13:24:26 EDT 2017", comments = "based on groovy-2.4.7-sources.jar")
public class CpsDefaultGroovyStaticMethods {


    public static Thread start(Thread self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "start", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyStaticMethods.class, "start", self, closure))) {
            return DefaultGroovyStaticMethods.start(self, closure);
        }
        Builder b = new Builder(loc("start"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(46, CpsDefaultGroovyStaticMethods.class, "createThread", b.constant(null), b.constant(false), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static Thread start(Thread self, String name, Closure closure) {
        if ((!Caller.isAsynchronous(self, "start", name, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyStaticMethods.class, "start", self, name, closure))) {
            return DefaultGroovyStaticMethods.start(self, name, closure);
        }
        Builder b = new Builder(loc("start"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "name", "closure"), b.block(b.return_(b.staticCall(54, CpsDefaultGroovyStaticMethods.class, "createThread", b.localVariable("name"), b.constant(false), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, name, closure);
    }

    public static Thread startDaemon(Thread self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "startDaemon", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyStaticMethods.class, "startDaemon", self, closure))) {
            return DefaultGroovyStaticMethods.startDaemon(self, closure);
        }
        Builder b = new Builder(loc("startDaemon"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(69, CpsDefaultGroovyStaticMethods.class, "createThread", b.constant(null), b.constant(true), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static Thread startDaemon(Thread self, String name, Closure closure) {
        if ((!Caller.isAsynchronous(self, "startDaemon", name, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyStaticMethods.class, "startDaemon", self, name, closure))) {
            return DefaultGroovyStaticMethods.startDaemon(self, name, closure);
        }
        Builder b = new Builder(loc("startDaemon"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "name", "closure"), b.block(b.return_(b.staticCall(83, CpsDefaultGroovyStaticMethods.class, "createThread", b.localVariable("name"), b.constant(true), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, name, closure);
    }

    public static void sleep(Object self, long milliseconds, Closure onInterrupt) {
        if ((!Caller.isAsynchronous(self, "sleep", milliseconds, onInterrupt))&&(!Caller.isAsynchronous(CpsDefaultGroovyStaticMethods.class, "sleep", self, milliseconds, onInterrupt))) {
            DefaultGroovyStaticMethods.sleep(self, milliseconds, onInterrupt);
            return ;
        }
        Builder b = new Builder(loc("sleep"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "milliseconds", "onInterrupt"), b.block(b.staticCall(149, CpsDefaultGroovyStaticMethods.class, "sleepImpl", b.localVariable("milliseconds"), b.localVariable("onInterrupt"))));
        throw new CpsCallableInvocation(f, null, self, milliseconds, onInterrupt);
    }

    private static MethodLocation loc(String methodName) {
        return new MethodLocation(CpsDefaultGroovyStaticMethods.class, methodName);
    }

}
