package com.cloudbees.groovy.cps

import com.cloudbees.groovy.cps.impl.Caller
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation
import com.cloudbees.groovy.cps.impl.CpsFunction
import org.codehaus.groovy.runtime.DefaultGroovyMethods
import org.codehaus.groovy.runtime.InvokerHelper

/**
 *
 * TODO: any way to apply CPS transformation?
 *
 * @author Kohsuke Kawaguchi
 */
public class CpsDefaultGroovyMethods {
    private static MethodLocation loc(String methodName) {
        return new MethodLocation(CpsDefaultGroovyMethods.class,methodName);
    }

    /**
     * Interception is successful. The trick is to pre-translate this method into CPS.
     */
    public static <T> T each(T self, Closure closure) {
        if (!Caller.isAsynchronous(self,"each",closure)
         && !Caller.isAsynchronous(CpsDefaultGroovyMethods.class,"each",self,closure))
            return DefaultGroovyMethods.each(self,closure);

        /*
        each(InvokerHelper.asIterator(self), closure);
        return self;
        */

        def b = new Builder(loc("each"));
        def f = new CpsFunction(["self", "closure"], b.block(
                b.staticCall(-1, CpsDefaultGroovyMethods.class, "each",
                        b.staticCall(-1, InvokerHelper.class, "asIterator",
                                b.localVariable("self")
                        ),
                        b.localVariable("closure")
                ),
                b.return_(b.localVariable("self"))
        ));

        throw new CpsCallableInvocation(f,null,self,closure);
    }

    public static <T> Iterator<T> each(Iterator<T> iter, Closure closure) {
        if (!Caller.isAsynchronous(iter,"each",closure)
         && !Caller.isAsynchronous(CpsDefaultGroovyMethods.class,"each",iter,closure))
            return DefaultGroovyMethods.each(iter,closure);

/*
        while (iter.hasNext()) {
            Object arg = iter.next();
            closure.call(arg);
        }
        return iter;
*/


        def b = new Builder(loc("each"));
        def $iter = b.localVariable("iter")

        def f = new CpsFunction(["iter", "closure"], b.block(
            b.while_(null, b.functionCall(1, $iter,"hasNext"),
                b.block(
                    b.declareVariable(2,Object.class,"arg", b.functionCall(2, $iter,"next")),
                    b.functionCall(3, b.localVariable("closure"), "call", b.localVariable("arg"))
                )
            ),
            b.return_($iter)
        ));

        throw new CpsCallableInvocation(f,null,iter,closure);
    }
}
