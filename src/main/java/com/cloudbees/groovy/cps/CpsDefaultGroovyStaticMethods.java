
package com.cloudbees.groovy.cps;

import java.util.Arrays;
import javax.annotation.Generated;
import com.cloudbees.groovy.cps.impl.Caller;
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.CpsFunction;
import groovy.lang.Closure;
import org.codehaus.groovy.runtime.DefaultGroovyStaticMethods;

@Generated(value = "com.cloudbees.groovy.cps.tool.Translator", date = "Mon May 15 17:17:37 EDT 2017", comments = "based on groovy-2.4.7-sources.jar")
public class CpsDefaultGroovyStaticMethods {


    public static Thread start(Thread self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "start", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyStaticMethods.class, "start"))) {
            return DefaultGroovyStaticMethods.start(self, closure);
        }
        throw new UnsupportedOperationException("org.codehaus.groovy.runtime.DefaultGroovyStaticMethods.start(java.lang.Thread,groovy.lang.Closure) is not yet supported for translation; use another idiom, or wrap in @NonCPS");
    }

    public static Thread start(Thread self, String name, Closure closure) {
        if ((!Caller.isAsynchronous(self, "start", name, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyStaticMethods.class, "start"))) {
            return DefaultGroovyStaticMethods.start(self, name, closure);
        }
        throw new UnsupportedOperationException("org.codehaus.groovy.runtime.DefaultGroovyStaticMethods.start(java.lang.Thread,java.lang.String,groovy.lang.Closure) is not yet supported for translation; use another idiom, or wrap in @NonCPS");
    }

    public static Thread startDaemon(Thread self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "startDaemon", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyStaticMethods.class, "startDaemon"))) {
            return DefaultGroovyStaticMethods.startDaemon(self, closure);
        }
        throw new UnsupportedOperationException("org.codehaus.groovy.runtime.DefaultGroovyStaticMethods.startDaemon(java.lang.Thread,groovy.lang.Closure) is not yet supported for translation; use another idiom, or wrap in @NonCPS");
    }

    public static Thread startDaemon(Thread self, String name, Closure closure) {
        if ((!Caller.isAsynchronous(self, "startDaemon", name, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyStaticMethods.class, "startDaemon"))) {
            return DefaultGroovyStaticMethods.startDaemon(self, name, closure);
        }
        throw new UnsupportedOperationException("org.codehaus.groovy.runtime.DefaultGroovyStaticMethods.startDaemon(java.lang.Thread,java.lang.String,groovy.lang.Closure) is not yet supported for translation; use another idiom, or wrap in @NonCPS");
    }

    static Thread createThread(String name, boolean daemon, Closure closure) {
        return CpsDefaultGroovyStaticMethods.$createThread__java_lang_String__boolean__groovy_lang_Closure(name, daemon, closure);
    }

    private static Thread $createThread__java_lang_String__boolean__groovy_lang_Closure(String name, boolean daemon, Closure closure) {
        Builder b = new Builder(loc("createThread"));
        CpsFunction f = new CpsFunction(Arrays.asList("name", "daemon", "closure"), b.block(b.declareVariable(86, Thread.class, "thread", b.ternaryOp(b.compareNotEqual(86, b.localVariable("name"), b.constant(null)), b.new_(86, Thread.class, b.localVariable("closure"), b.localVariable("name")), b.new_(87, Thread.class, b.localVariable("closure")))), b.if_(b.localVariable("daemon"), b.functionCall(87, b.localVariable("thread"), "setDaemon", b.constant(true))), b.functionCall(88, b.localVariable("thread"), "start"), b.return_(b.localVariable("thread"))));
        throw new CpsCallableInvocation(f, null, name, daemon, closure);
    }

    static void sleepImpl(long millis, Closure closure) {
        CpsDefaultGroovyStaticMethods.$sleepImpl__long__groovy_lang_Closure(millis, closure);
    }

    private static void $sleepImpl__long__groovy_lang_Closure(long millis, Closure closure) {
        Builder b = new Builder(loc("sleepImpl"));
        CpsFunction f = new CpsFunction(Arrays.asList("millis", "closure"), b.block(b.declareVariable(113, long.class, "start", b.functionCall(114, b.constant(System.class), "currentTimeMillis")), b.declareVariable(115, long.class, "rest", b.localVariable("millis")), b.declareVariable(116, long.class, "current", null), b.while_(null, b.greaterThan(117, b.localVariable("rest"), b.constant(0)), b.block(b.tryCatch(b.block(b.functionCall(119, b.constant(Thread.class), "sleep", b.localVariable("rest")), b.assign(121, b.localVariable("rest"), b.constant(0))), null)))));
        throw new CpsCallableInvocation(f, null, millis, closure);
    }

    public static void sleep(Object self, long milliseconds, Closure onInterrupt) {
        if ((!Caller.isAsynchronous(self, "sleep", milliseconds, onInterrupt))&&(!Caller.isAsynchronous(CpsDefaultGroovyStaticMethods.class, "sleep", self, milliseconds, onInterrupt))) {
            DefaultGroovyStaticMethods.sleep(self, milliseconds, onInterrupt);
            return ;
        }
        CpsDefaultGroovyStaticMethods.$sleep__java_lang_Object__long__groovy_lang_Closure(self, milliseconds, onInterrupt);
    }

    private static void $sleep__java_lang_Object__long__groovy_lang_Closure(Object self, long milliseconds, Closure onInterrupt) {
        Builder b = new Builder(loc("sleep"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "milliseconds", "onInterrupt"), b.block(b.staticCall(149, CpsDefaultGroovyStaticMethods.class, "$sleepImpl__long__groovy_lang_Closure", b.localVariable("milliseconds"), b.localVariable("onInterrupt"))));
        throw new CpsCallableInvocation(f, null, self, milliseconds, onInterrupt);
    }

    private static MethodLocation loc(String methodName) {
        return new MethodLocation(CpsDefaultGroovyStaticMethods.class, methodName);
    }

}
