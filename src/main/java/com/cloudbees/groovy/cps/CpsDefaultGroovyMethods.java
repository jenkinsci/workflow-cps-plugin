
package com.cloudbees.groovy.cps;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.SortedSet;
import javax.annotation.Generated;
import com.cloudbees.groovy.cps.impl.Caller;
import com.cloudbees.groovy.cps.impl.CpsCallableInvocation;
import com.cloudbees.groovy.cps.impl.CpsFunction;
import groovy.lang.Closure;
import groovy.lang.DelegatingMetaClass;
import groovy.lang.ExpandoMetaClass;
import groovy.lang.GroovyObject;
import groovy.lang.GroovyRuntimeException;
import groovy.lang.GroovySystem;
import groovy.lang.ListWithDefault;
import groovy.lang.MapWithDefault;
import groovy.lang.MetaClass;
import groovy.lang.MetaClassImpl;
import groovy.lang.MetaClassRegistry;
import groovy.util.ClosureComparator;
import groovy.util.GroovyCollections;
import groovy.util.OrderBy;
import groovy.util.PermutationGenerator;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.codehaus.groovy.runtime.DefaultGroovyMethodsSupport;
import org.codehaus.groovy.runtime.GroovyCategorySupport;
import org.codehaus.groovy.runtime.InvokerHelper;
import org.codehaus.groovy.runtime.ReverseListIterator;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.codehaus.groovy.runtime.callsite.BooleanClosureWrapper;
import org.codehaus.groovy.util.ArrayIterator;

@Generated(value = "com.cloudbees.groovy.cps.tool.Translator", date = "Fri May 12 13:24:26 EDT 2017", comments = "based on groovy-2.4.7-sources.jar")
public class CpsDefaultGroovyMethods {


    public static<T >Object identity(Object self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "identity", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "identity", self, closure))) {
            return DefaultGroovyMethods.identity(self, closure);
        }
        Builder b = new Builder(loc("identity"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.functionCall(200, b.constant(DefaultGroovyMethods.class), "with", b.localVariable("self"), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T, U >Object with(Object self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "with", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "with", self, closure))) {
            return DefaultGroovyMethods.with(self, closure);
        }
        Builder b = new Builder(loc("with"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(238, Closure.class, "clonedClosure", b.cast(239, b.functionCall(239, b.localVariable("closure"), "clone"), Closure.class, false)), b.functionCall(240, b.localVariable("clonedClosure"), "setResolveStrategy", b.property(240, b.constant(Closure.class), "DELEGATE_FIRST")), b.functionCall(241, b.localVariable("clonedClosure"), "setDelegate", b.localVariable("self")), b.return_(b.functionCall(242, b.localVariable("clonedClosure"), "call", b.localVariable("self")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Object use(Object self, Class categoryClass, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "use", categoryClass, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "use", self, categoryClass, closure))) {
            return DefaultGroovyMethods.use(self, categoryClass, closure);
        }
        Builder b = new Builder(loc("use"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "categoryClass", "closure"), b.block(b.return_(b.functionCall(407, b.constant(GroovyCategorySupport.class), "use", b.localVariable("categoryClass"), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, categoryClass, closure);
    }

    public static<T >Object use(Object self, List<Class> categoryClassList, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "use", categoryClassList, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "use", self, categoryClassList, closure))) {
            return DefaultGroovyMethods.use(self, categoryClassList, closure);
        }
        Builder b = new Builder(loc("use"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "categoryClassList", "closure"), b.block(b.return_(b.functionCall(488, b.constant(GroovyCategorySupport.class), "use", b.localVariable("categoryClassList"), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, categoryClassList, closure);
    }

    public static void addShutdownHook(Object self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "addShutdownHook", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "addShutdownHook", self, closure))) {
            DefaultGroovyMethods.addShutdownHook(self, closure);
            return ;
        }
        Builder b = new Builder(loc("addShutdownHook"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.functionCall(499, b.functionCall(499, b.constant(Runtime.class), "getRuntime"), "addShutdownHook", b.new_(499, Thread.class, b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Iterator<Object> unique(Iterator<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "unique", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "unique", self, closure))) {
            return DefaultGroovyMethods.unique(self, closure);
        }
        Builder b = new Builder(loc("unique"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.functionCall(1140, b.staticCall(1140, CpsDefaultGroovyMethods.class, "toList", b.cast(1140, b.staticCall(1140, CpsDefaultGroovyMethods.class, "unique", b.staticCall(1140, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), b.localVariable("closure")), Iterable.class, false)), "listIterator"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Collection<Object> unique(Collection<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "unique", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "unique", self, closure))) {
            return DefaultGroovyMethods.unique(self, closure);
        }
        Builder b = new Builder(loc("unique"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(1164, CpsDefaultGroovyMethods.class, "unique", b.localVariable("self"), b.constant(true), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >List<Object> unique(List<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "unique", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "unique", self, closure))) {
            return DefaultGroovyMethods.unique(self, closure);
        }
        Builder b = new Builder(loc("unique"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.cast(1188, b.staticCall(1188, CpsDefaultGroovyMethods.class, "unique", b.cast(1188, b.localVariable("self"), Collection.class, false), b.constant(true), b.localVariable("closure")), List.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Collection<Object> unique(Collection<Object> self, boolean mutate, Closure closure) {
        if ((!Caller.isAsynchronous(self, "unique", mutate, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "unique", self, mutate, closure))) {
            return DefaultGroovyMethods.unique(self, mutate, closure);
        }
        Builder b = new Builder(loc("unique"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "mutate", "closure"), b.block(b.declareVariable(1220, int.class, "params", b.functionCall(1220, b.localVariable("closure"), "getMaximumNumberOfParameters")), b.if_(b.compareEqual(1221, b.localVariable("params"), b.constant(1)), b.block(b.assign(1222, b.localVariable("self"), b.staticCall(1222, CpsDefaultGroovyMethods.class, "unique", b.localVariable("self"), b.localVariable("mutate"), b.new_(1222, OrderBy.class, b.localVariable("closure"), b.constant(true))))), b.block(b.assign(1224, b.localVariable("self"), b.staticCall(1224, CpsDefaultGroovyMethods.class, "unique", b.localVariable("self"), b.localVariable("mutate"), b.new_(1224, ClosureComparator.class, b.localVariable("closure")))))), b.return_(b.localVariable("self"))));
        throw new CpsCallableInvocation(f, null, self, mutate, closure);
    }

    public static<T >List<Object> unique(List<Object> self, boolean mutate, Closure closure) {
        if ((!Caller.isAsynchronous(self, "unique", mutate, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "unique", self, mutate, closure))) {
            return DefaultGroovyMethods.unique(self, mutate, closure);
        }
        Builder b = new Builder(loc("unique"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "mutate", "closure"), b.block(b.return_(b.cast(1257, b.staticCall(1257, CpsDefaultGroovyMethods.class, "unique", b.cast(1257, b.localVariable("self"), Collection.class, false), b.localVariable("mutate"), b.localVariable("closure")), List.class, false))));
        throw new CpsCallableInvocation(f, null, self, mutate, closure);
    }

    public static<T >Object each(Object self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "each", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "each", self, closure))) {
            return DefaultGroovyMethods.each(self, closure);
        }
        Builder b = new Builder(loc("each"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.staticCall(1890, CpsDefaultGroovyMethods.class, "each", b.functionCall(1890, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self")), b.localVariable("closure")), b.return_(b.localVariable("self"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Object eachWithIndex(Object self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "eachWithIndex", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "eachWithIndex", self, closure))) {
            return DefaultGroovyMethods.eachWithIndex(self, closure);
        }
        Builder b = new Builder(loc("eachWithIndex"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(1905, Object[].class, "args", b.newArray(1905, Object.class, b.constant(2))), b.declareVariable(1906, int.class, "counter", b.constant(0)), b.forLoop(null, b.sequence(b.declareVariable(1907, Iterator.class, "iter", b.functionCall(1907, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self")))), b.functionCall(1907, b.localVariable("iter"), "hasNext"), b.sequence(), b.block(b.assign(1908, b.array(1908, b.localVariable("args"), b.constant(0)), b.functionCall(1908, b.localVariable("iter"), "next")), b.assign(1909, b.array(1909, b.localVariable("args"), b.constant(1)), b.postfixInc(1909, b.localVariable("counter"))), b.functionCall(1910, b.localVariable("closure"), "call", b.localVariable("args")))), b.return_(b.localVariable("self"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Iterable<Object> eachWithIndex(Iterable<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "eachWithIndex", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "eachWithIndex", self, closure))) {
            return DefaultGroovyMethods.eachWithIndex(self, closure);
        }
        Builder b = new Builder(loc("eachWithIndex"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.staticCall(1926, CpsDefaultGroovyMethods.class, "eachWithIndex", b.functionCall(1926, b.localVariable("self"), "iterator"), b.localVariable("closure")), b.return_(b.localVariable("self"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Iterator<Object> eachWithIndex(Iterator<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "eachWithIndex", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "eachWithIndex", self, closure))) {
            return DefaultGroovyMethods.eachWithIndex(self, closure);
        }
        Builder b = new Builder(loc("eachWithIndex"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(1941, Object[].class, "args", b.newArray(1941, Object.class, b.constant(2))), b.declareVariable(1942, int.class, "counter", b.constant(0)), b.while_(null, b.functionCall(1943, b.localVariable("self"), "hasNext"), b.block(b.assign(1944, b.array(1944, b.localVariable("args"), b.constant(0)), b.functionCall(1944, b.localVariable("self"), "next")), b.assign(1945, b.array(1945, b.localVariable("args"), b.constant(1)), b.postfixInc(1945, b.localVariable("counter"))), b.functionCall(1946, b.localVariable("closure"), "call", b.localVariable("args")))), b.return_(b.localVariable("self"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Collection<Object> eachWithIndex(Collection<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "eachWithIndex", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "eachWithIndex", self, closure))) {
            return DefaultGroovyMethods.eachWithIndex(self, closure);
        }
        Builder b = new Builder(loc("eachWithIndex"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.cast(1962, b.staticCall(1962, CpsDefaultGroovyMethods.class, "eachWithIndex", b.cast(1962, b.localVariable("self"), Iterable.class, false), b.localVariable("closure")), Collection.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >List<Object> eachWithIndex(List<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "eachWithIndex", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "eachWithIndex", self, closure))) {
            return DefaultGroovyMethods.eachWithIndex(self, closure);
        }
        Builder b = new Builder(loc("eachWithIndex"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.cast(1976, b.staticCall(1976, CpsDefaultGroovyMethods.class, "eachWithIndex", b.cast(1976, b.localVariable("self"), Iterable.class, false), b.localVariable("closure")), List.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Set<Object> eachWithIndex(Set<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "eachWithIndex", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "eachWithIndex", self, closure))) {
            return DefaultGroovyMethods.eachWithIndex(self, closure);
        }
        Builder b = new Builder(loc("eachWithIndex"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.cast(1990, b.staticCall(1990, CpsDefaultGroovyMethods.class, "eachWithIndex", b.cast(1990, b.localVariable("self"), Iterable.class, false), b.localVariable("closure")), Set.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >SortedSet<Object> eachWithIndex(SortedSet<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "eachWithIndex", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "eachWithIndex", self, closure))) {
            return DefaultGroovyMethods.eachWithIndex(self, closure);
        }
        Builder b = new Builder(loc("eachWithIndex"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.cast(2004, b.staticCall(2004, CpsDefaultGroovyMethods.class, "eachWithIndex", b.cast(2004, b.localVariable("self"), Iterable.class, false), b.localVariable("closure")), SortedSet.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Iterable<Object> each(Iterable<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "each", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "each", self, closure))) {
            return DefaultGroovyMethods.each(self, closure);
        }
        Builder b = new Builder(loc("each"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.staticCall(2015, CpsDefaultGroovyMethods.class, "each", b.functionCall(2015, b.localVariable("self"), "iterator"), b.localVariable("closure")), b.return_(b.localVariable("self"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Iterator<Object> each(Iterator<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "each", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "each", self, closure))) {
            return DefaultGroovyMethods.each(self, closure);
        }
        Builder b = new Builder(loc("each"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.while_(null, b.functionCall(2028, b.localVariable("self"), "hasNext"), b.block(b.declareVariable(2029, Object.class, "arg", b.functionCall(2029, b.localVariable("self"), "next")), b.functionCall(2030, b.localVariable("closure"), "call", b.localVariable("arg")))), b.return_(b.localVariable("self"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Collection<Object> each(Collection<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "each", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "each", self, closure))) {
            return DefaultGroovyMethods.each(self, closure);
        }
        Builder b = new Builder(loc("each"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.cast(2044, b.staticCall(2044, CpsDefaultGroovyMethods.class, "each", b.cast(2044, b.localVariable("self"), Iterable.class, false), b.localVariable("closure")), Collection.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >List<Object> each(List<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "each", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "each", self, closure))) {
            return DefaultGroovyMethods.each(self, closure);
        }
        Builder b = new Builder(loc("each"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.cast(2056, b.staticCall(2056, CpsDefaultGroovyMethods.class, "each", b.cast(2056, b.localVariable("self"), Iterable.class, false), b.localVariable("closure")), List.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Set<Object> each(Set<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "each", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "each", self, closure))) {
            return DefaultGroovyMethods.each(self, closure);
        }
        Builder b = new Builder(loc("each"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.cast(2068, b.staticCall(2068, CpsDefaultGroovyMethods.class, "each", b.cast(2068, b.localVariable("self"), Iterable.class, false), b.localVariable("closure")), Set.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >SortedSet<Object> each(SortedSet<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "each", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "each", self, closure))) {
            return DefaultGroovyMethods.each(self, closure);
        }
        Builder b = new Builder(loc("each"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.cast(2080, b.staticCall(2080, CpsDefaultGroovyMethods.class, "each", b.cast(2080, b.localVariable("self"), Iterable.class, false), b.localVariable("closure")), SortedSet.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, V >Map<Object, Object> each(Map<Object, Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "each", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "each", self, closure))) {
            return DefaultGroovyMethods.each(self, closure);
        }
        Builder b = new Builder(loc("each"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.forInLoop(2106, null, java.util.Map.Entry.class, "entry", b.functionCall(2106, b.localVariable("self"), "entrySet"), b.block(b.staticCall(2107, CpsDefaultGroovyMethods.class, "callClosureForMapEntry", b.localVariable("closure"), b.localVariable("entry")))), b.return_(b.localVariable("self"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, V >Map<Object, Object> reverseEach(Map<Object, Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "reverseEach", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "reverseEach", self, closure))) {
            return DefaultGroovyMethods.reverseEach(self, closure);
        }
        Builder b = new Builder(loc("reverseEach"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(2127, Iterator.class, "entries", b.staticCall(2127, CpsDefaultGroovyMethods.class, "reverse", b.functionCall(2127, b.functionCall(2127, b.localVariable("self"), "entrySet"), "iterator"))), b.while_(null, b.functionCall(2128, b.localVariable("entries"), "hasNext"), b.block(b.staticCall(2129, CpsDefaultGroovyMethods.class, "callClosureForMapEntry", b.localVariable("closure"), b.functionCall(2129, b.localVariable("entries"), "next")))), b.return_(b.localVariable("self"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, V >Map<Object, Object> eachWithIndex(Map<Object, Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "eachWithIndex", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "eachWithIndex", self, closure))) {
            return DefaultGroovyMethods.eachWithIndex(self, closure);
        }
        Builder b = new Builder(loc("eachWithIndex"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(2153, int.class, "counter", b.constant(0)), b.forInLoop(2154, null, java.util.Map.Entry.class, "entry", b.functionCall(2154, b.localVariable("self"), "entrySet"), b.block(b.staticCall(2155, CpsDefaultGroovyMethods.class, "callClosureForMapEntryAndCounter", b.localVariable("closure"), b.localVariable("entry"), b.postfixInc(2155, b.localVariable("counter"))))), b.return_(b.localVariable("self"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >List<Object> reverseEach(List<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "reverseEach", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "reverseEach", self, closure))) {
            return DefaultGroovyMethods.reverseEach(self, closure);
        }
        Builder b = new Builder(loc("reverseEach"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.staticCall(2172, CpsDefaultGroovyMethods.class, "each", b.new_(2172, ReverseListIterator.class, b.localVariable("self")), b.localVariable("closure")), b.return_(b.localVariable("self"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Object[] reverseEach(Object[] self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "reverseEach", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "reverseEach", self, closure))) {
            return DefaultGroovyMethods.reverseEach(self, closure);
        }
        Builder b = new Builder(loc("reverseEach"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.staticCall(2185, CpsDefaultGroovyMethods.class, "each", b.new_(2185, ReverseListIterator.class, b.functionCall(2185, b.constant(Arrays.class), "asList", b.localVariable("self"))), b.localVariable("closure")), b.return_(b.localVariable("self"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static boolean every(Object self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "every", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "every", self, closure))) {
            return DefaultGroovyMethods.every(self, closure);
        }
        Builder b = new Builder(loc("every"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(2203, BooleanClosureWrapper.class, "bcw", b.new_(2203, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forLoop(null, b.sequence(b.declareVariable(2204, Iterator.class, "iter", b.functionCall(2204, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self")))), b.functionCall(2204, b.localVariable("iter"), "hasNext"), b.sequence(), b.block(b.if_(b.not(2205, b.functionCall(2205, b.localVariable("bcw"), "call", b.functionCall(2205, b.localVariable("iter"), "next"))), b.block(b.return_(b.constant(false)))))), b.return_(b.constant(true))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >boolean every(Iterator<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "every", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "every", self, closure))) {
            return DefaultGroovyMethods.every(self, closure);
        }
        Builder b = new Builder(loc("every"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(2226, BooleanClosureWrapper.class, "bcw", b.new_(2226, BooleanClosureWrapper.class, b.localVariable("closure"))), b.while_(null, b.functionCall(2227, b.localVariable("self"), "hasNext"), b.block(b.if_(b.not(2228, b.functionCall(2228, b.localVariable("bcw"), "call", b.functionCall(2228, b.localVariable("self"), "next"))), b.block(b.return_(b.constant(false)))))), b.return_(b.constant(true))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >boolean every(Iterable<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "every", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "every", self, closure))) {
            return DefaultGroovyMethods.every(self, closure);
        }
        Builder b = new Builder(loc("every"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(2249, CpsDefaultGroovyMethods.class, "every", b.functionCall(2249, b.localVariable("self"), "iterator"), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, V >boolean every(Map<Object, Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "every", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "every", self, closure))) {
            return DefaultGroovyMethods.every(self, closure);
        }
        Builder b = new Builder(loc("every"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(2268, BooleanClosureWrapper.class, "bcw", b.new_(2268, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forInLoop(2269, null, java.util.Map.Entry.class, "entry", b.functionCall(2269, b.localVariable("self"), "entrySet"), b.block(b.if_(b.not(2270, b.functionCall(2270, b.localVariable("bcw"), "callForMap", b.localVariable("entry"))), b.block(b.return_(b.constant(false)))))), b.return_(b.constant(true))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static boolean any(Object self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "any", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "any", self, closure))) {
            return DefaultGroovyMethods.any(self, closure);
        }
        Builder b = new Builder(loc("any"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(2307, BooleanClosureWrapper.class, "bcw", b.new_(2307, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forLoop(null, b.sequence(b.declareVariable(2308, Iterator.class, "iter", b.functionCall(2308, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self")))), b.functionCall(2308, b.localVariable("iter"), "hasNext"), b.sequence(), b.block(b.if_(b.functionCall(2309, b.localVariable("bcw"), "call", b.functionCall(2309, b.localVariable("iter"), "next")), b.return_(b.constant(true))))), b.return_(b.constant(false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >boolean any(Iterator<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "any", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "any", self, closure))) {
            return DefaultGroovyMethods.any(self, closure);
        }
        Builder b = new Builder(loc("any"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(2324, BooleanClosureWrapper.class, "bcw", b.new_(2324, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forLoop(null, b.sequence(b.declareVariable(2325, Iterator.class, "iter", b.localVariable("self"))), b.functionCall(2325, b.localVariable("iter"), "hasNext"), b.sequence(), b.block(b.if_(b.functionCall(2326, b.localVariable("bcw"), "call", b.functionCall(2326, b.localVariable("iter"), "next")), b.return_(b.constant(true))))), b.return_(b.constant(false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >boolean any(Iterable<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "any", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "any", self, closure))) {
            return DefaultGroovyMethods.any(self, closure);
        }
        Builder b = new Builder(loc("any"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(2341, BooleanClosureWrapper.class, "bcw", b.new_(2341, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forLoop(null, b.sequence(b.declareVariable(2342, Iterator.class, "iter", b.functionCall(2342, b.localVariable("self"), "iterator"))), b.functionCall(2342, b.localVariable("iter"), "hasNext"), b.sequence(), b.block(b.if_(b.functionCall(2343, b.localVariable("bcw"), "call", b.functionCall(2343, b.localVariable("iter"), "next")), b.return_(b.constant(true))))), b.return_(b.constant(false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, V >boolean any(Map<Object, Object> self, Closure<?> closure) {
        if ((!Caller.isAsynchronous(self, "any", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "any", self, closure))) {
            return DefaultGroovyMethods.any(self, closure);
        }
        Builder b = new Builder(loc("any"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(2365, BooleanClosureWrapper.class, "bcw", b.new_(2365, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forInLoop(2366, null, java.util.Map.Entry.class, "entry", b.functionCall(2366, b.localVariable("self"), "entrySet"), b.block(b.if_(b.functionCall(2367, b.localVariable("bcw"), "callForMap", b.localVariable("entry")), b.block(b.return_(b.constant(true)))))), b.return_(b.constant(false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Number count(Iterator<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "count", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "count", self, closure))) {
            return DefaultGroovyMethods.count(self, closure);
        }
        Builder b = new Builder(loc("count"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(2664, long.class, "answer", b.constant(0)), b.declareVariable(2665, BooleanClosureWrapper.class, "bcw", b.new_(2665, BooleanClosureWrapper.class, b.localVariable("closure"))), b.while_(null, b.functionCall(2666, b.localVariable("self"), "hasNext"), b.block(b.if_(b.functionCall(2667, b.localVariable("bcw"), "call", b.functionCall(2667, b.localVariable("self"), "next")), b.block(b.prefixInc(2668, b.localVariable("answer")))))), b.if_(b.lessThanEqual(2672, b.localVariable("answer"), b.property(2672, b.constant(Integer.class), "MAX_VALUE")), b.return_(b.cast(2672, b.localVariable("answer"), int.class, false))), b.return_(b.localVariable("answer"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Number count(Iterable<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "count", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "count", self, closure))) {
            return DefaultGroovyMethods.count(self, closure);
        }
        Builder b = new Builder(loc("count"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(2723, CpsDefaultGroovyMethods.class, "count", b.functionCall(2723, b.localVariable("self"), "iterator"), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, V >Number count(Map<Object, Object> self, Closure<?> closure) {
        if ((!Caller.isAsynchronous(self, "count", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "count", self, closure))) {
            return DefaultGroovyMethods.count(self, closure);
        }
        Builder b = new Builder(loc("count"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(2740, long.class, "answer", b.constant(0)), b.declareVariable(2741, BooleanClosureWrapper.class, "bcw", b.new_(2741, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forInLoop(2742, null, Object.class, "entry", b.functionCall(2742, b.localVariable("self"), "entrySet"), b.block(b.if_(b.functionCall(2743, b.localVariable("bcw"), "callForMap", b.cast(2743, b.localVariable("entry"), java.util.Map.Entry.class, false)), b.block(b.prefixInc(2744, b.localVariable("answer")))))), b.if_(b.lessThanEqual(2748, b.localVariable("answer"), b.property(2748, b.constant(Integer.class), "MAX_VALUE")), b.return_(b.cast(2748, b.localVariable("answer"), int.class, false))), b.return_(b.localVariable("answer"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Number count(Object[] self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "count", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "count", self, closure))) {
            return DefaultGroovyMethods.count(self, closure);
        }
        Builder b = new Builder(loc("count"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(2775, CpsDefaultGroovyMethods.class, "count", b.cast(2775, b.functionCall(2775, b.constant(Arrays.class), "asList", b.localVariable("self")), Iterable.class, false), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >List<Object> collect(Object self, Closure<Object> transform) {
        if ((!Caller.isAsynchronous(self, "collect", transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collect", self, transform))) {
            return DefaultGroovyMethods.collect(self, transform);
        }
        Builder b = new Builder(loc("collect"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "transform"), b.block(b.return_(b.cast(3095, b.staticCall(3095, CpsDefaultGroovyMethods.class, "collect", b.localVariable("self"), b.new_(3095, ArrayList.class), b.localVariable("transform")), List.class, false))));
        throw new CpsCallableInvocation(f, null, self, transform);
    }

    public static<T >Collection<Object> collect(Object self, Collection<Object> collector, Closure<?> transform) {
        if ((!Caller.isAsynchronous(self, "collect", collector, transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collect", self, collector, transform))) {
            return DefaultGroovyMethods.collect(self, collector, transform);
        }
        Builder b = new Builder(loc("collect"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "collector", "transform"), b.block(b.forLoop(null, b.sequence(b.declareVariable(3123, Iterator.class, "iter", b.functionCall(3123, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self")))), b.functionCall(3123, b.localVariable("iter"), "hasNext"), b.sequence(), b.block(b.functionCall(3124, b.localVariable("collector"), "add", b.functionCall(3124, b.localVariable("transform"), "call", b.functionCall(3124, b.localVariable("iter"), "next"))))), b.return_(b.localVariable("collector"))));
        throw new CpsCallableInvocation(f, null, self, collector, transform);
    }

    public static<S, T >List<Object> collect(Collection<Object> self, Closure<Object> transform) {
        if ((!Caller.isAsynchronous(self, "collect", transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collect", self, transform))) {
            return DefaultGroovyMethods.collect(self, transform);
        }
        Builder b = new Builder(loc("collect"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "transform"), b.block(b.return_(b.cast(3140, b.staticCall(3140, CpsDefaultGroovyMethods.class, "collect", b.localVariable("self"), b.new_(3140, ArrayList.class, b.functionCall(3140, b.localVariable("self"), "size")), b.localVariable("transform")), List.class, false))));
        throw new CpsCallableInvocation(f, null, self, transform);
    }

    public static<T, E >Collection<Object> collect(Collection<Object> self, Collection<Object> collector, Closure<?> transform) {
        if ((!Caller.isAsynchronous(self, "collect", collector, transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collect", self, collector, transform))) {
            return DefaultGroovyMethods.collect(self, collector, transform);
        }
        Builder b = new Builder(loc("collect"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "collector", "transform"), b.block(b.forInLoop(3169, null, Object.class, "item", b.localVariable("self"), b.block(b.functionCall(3170, b.localVariable("collector"), "add", b.functionCall(3170, b.localVariable("transform"), "call", b.localVariable("item"))), b.if_(b.compareEqual(3171, b.functionCall(3171, b.localVariable("transform"), "getDirective"), b.property(3171, b.constant(Closure.class), "DONE")), b.block(b.break_(null))))), b.return_(b.localVariable("collector"))));
        throw new CpsCallableInvocation(f, null, self, collector, transform);
    }

    public static List collectNested(Collection self, Closure transform) {
        if ((!Caller.isAsynchronous(self, "collectNested", transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectNested", self, transform))) {
            return DefaultGroovyMethods.collectNested(self, transform);
        }
        Builder b = new Builder(loc("collectNested"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "transform"), b.block(b.return_(b.cast(3203, b.staticCall(3203, CpsDefaultGroovyMethods.class, "collectNested", b.cast(3203, b.localVariable("self"), Iterable.class, false), b.new_(3203, ArrayList.class, b.functionCall(3203, b.localVariable("self"), "size")), b.localVariable("transform")), List.class, false))));
        throw new CpsCallableInvocation(f, null, self, transform);
    }

    public static List collectNested(Iterable self, Closure transform) {
        if ((!Caller.isAsynchronous(self, "collectNested", transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectNested", self, transform))) {
            return DefaultGroovyMethods.collectNested(self, transform);
        }
        Builder b = new Builder(loc("collectNested"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "transform"), b.block(b.return_(b.cast(3220, b.staticCall(3220, CpsDefaultGroovyMethods.class, "collectNested", b.localVariable("self"), b.new_(3220, ArrayList.class), b.localVariable("transform")), List.class, false))));
        throw new CpsCallableInvocation(f, null, self, transform);
    }

    public static Collection collectNested(Iterable self, Collection collector, Closure transform) {
        if ((!Caller.isAsynchronous(self, "collectNested", collector, transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectNested", self, collector, transform))) {
            return DefaultGroovyMethods.collectNested(self, collector, transform);
        }
        Builder b = new Builder(loc("collectNested"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "collector", "transform"), b.block(b.forInLoop(3261, null, Object.class, "item", b.localVariable("self"), b.block(b.if_(b.instanceOf(3262, b.localVariable("item"), b.constant(Collection.class)), b.block(b.declareVariable(3263, Collection.class, "c", b.cast(3263, b.localVariable("item"), Collection.class, false)), b.functionCall(3264, b.localVariable("collector"), "add", b.staticCall(3264, CpsDefaultGroovyMethods.class, "collectNested", b.cast(3264, b.localVariable("c"), Iterable.class, false), b.functionCall(3264, b.constant(DefaultGroovyMethodsSupport.class), "createSimilarCollection", b.localVariable("collector"), b.functionCall(3264, b.localVariable("c"), "size")), b.localVariable("transform")))), b.block(b.functionCall(3266, b.localVariable("collector"), "add", b.functionCall(3266, b.localVariable("transform"), "call", b.localVariable("item"))))), b.if_(b.compareEqual(3268, b.functionCall(3268, b.localVariable("transform"), "getDirective"), b.property(3268, b.constant(Closure.class), "DONE")), b.block(b.break_(null))))), b.return_(b.localVariable("collector"))));
        throw new CpsCallableInvocation(f, null, self, collector, transform);
    }

    public static<T, E >List<Object> collectMany(Iterable<Object> self, Closure<Collection<?>> projection) {
        if ((!Caller.isAsynchronous(self, "collectMany", projection))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectMany", self, projection))) {
            return DefaultGroovyMethods.collectMany(self, projection);
        }
        Builder b = new Builder(loc("collectMany"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "projection"), b.block(b.return_(b.cast(3320, b.staticCall(3320, CpsDefaultGroovyMethods.class, "collectMany", b.localVariable("self"), b.new_(3320, ArrayList.class), b.localVariable("projection")), List.class, false))));
        throw new CpsCallableInvocation(f, null, self, projection);
    }

    public static<T, E >Collection<Object> collectMany(Iterable<Object> self, Collection<Object> collector, Closure<Collection<?>> projection) {
        if ((!Caller.isAsynchronous(self, "collectMany", collector, projection))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectMany", self, collector, projection))) {
            return DefaultGroovyMethods.collectMany(self, collector, projection);
        }
        Builder b = new Builder(loc("collectMany"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "collector", "projection"), b.block(b.forInLoop(3344, null, Object.class, "next", b.localVariable("self"), b.block(b.functionCall(3345, b.localVariable("collector"), "addAll", b.functionCall(3345, b.localVariable("projection"), "call", b.localVariable("next"))))), b.return_(b.localVariable("collector"))));
        throw new CpsCallableInvocation(f, null, self, collector, projection);
    }

    public static<T, K, V >Collection<Object> collectMany(Map<Object, Object> self, Collection<Object> collector, Closure<Collection<?>> projection) {
        if ((!Caller.isAsynchronous(self, "collectMany", collector, projection))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectMany", self, collector, projection))) {
            return DefaultGroovyMethods.collectMany(self, collector, projection);
        }
        Builder b = new Builder(loc("collectMany"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "collector", "projection"), b.block(b.forInLoop(3367, null, java.util.Map.Entry.class, "entry", b.functionCall(3367, b.localVariable("self"), "entrySet"), b.block(b.functionCall(3368, b.localVariable("collector"), "addAll", b.staticCall(3368, CpsDefaultGroovyMethods.class, "callClosureForMapEntry", b.localVariable("projection"), b.localVariable("entry"))))), b.return_(b.localVariable("collector"))));
        throw new CpsCallableInvocation(f, null, self, collector, projection);
    }

    public static<T, K, V >Collection<Object> collectMany(Map<Object, Object> self, Closure<Collection<?>> projection) {
        if ((!Caller.isAsynchronous(self, "collectMany", projection))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectMany", self, projection))) {
            return DefaultGroovyMethods.collectMany(self, projection);
        }
        Builder b = new Builder(loc("collectMany"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "projection"), b.block(b.return_(b.staticCall(3389, CpsDefaultGroovyMethods.class, "collectMany", b.localVariable("self"), b.new_(3389, ArrayList.class), b.localVariable("projection")))));
        throw new CpsCallableInvocation(f, null, self, projection);
    }

    public static<T, E >List<Object> collectMany(Object[] self, Closure<Collection<?>> projection) {
        if ((!Caller.isAsynchronous(self, "collectMany", projection))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectMany", self, projection))) {
            return DefaultGroovyMethods.collectMany(self, projection);
        }
        Builder b = new Builder(loc("collectMany"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "projection"), b.block(b.return_(b.staticCall(3409, CpsDefaultGroovyMethods.class, "collectMany", b.cast(3409, b.staticCall(3409, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), Iterable.class, false), b.localVariable("projection")))));
        throw new CpsCallableInvocation(f, null, self, projection);
    }

    public static<T, E >List<Object> collectMany(Iterator<Object> self, Closure<Collection<?>> projection) {
        if ((!Caller.isAsynchronous(self, "collectMany", projection))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectMany", self, projection))) {
            return DefaultGroovyMethods.collectMany(self, projection);
        }
        Builder b = new Builder(loc("collectMany"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "projection"), b.block(b.return_(b.staticCall(3429, CpsDefaultGroovyMethods.class, "collectMany", b.cast(3429, b.staticCall(3429, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), Iterable.class, false), b.localVariable("projection")))));
        throw new CpsCallableInvocation(f, null, self, projection);
    }

    public static<T, K, V >Collection<Object> collect(Map<Object, Object> self, Collection<Object> collector, Closure<?> transform) {
        if ((!Caller.isAsynchronous(self, "collect", collector, transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collect", self, collector, transform))) {
            return DefaultGroovyMethods.collect(self, collector, transform);
        }
        Builder b = new Builder(loc("collect"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "collector", "transform"), b.block(b.forInLoop(3445, null, java.util.Map.Entry.class, "entry", b.functionCall(3445, b.localVariable("self"), "entrySet"), b.block(b.functionCall(3446, b.localVariable("collector"), "add", b.staticCall(3446, CpsDefaultGroovyMethods.class, "callClosureForMapEntry", b.localVariable("transform"), b.localVariable("entry"))))), b.return_(b.localVariable("collector"))));
        throw new CpsCallableInvocation(f, null, self, collector, transform);
    }

    public static<T, K, V >List<Object> collect(Map<Object, Object> self, Closure<Object> transform) {
        if ((!Caller.isAsynchronous(self, "collect", transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collect", self, transform))) {
            return DefaultGroovyMethods.collect(self, transform);
        }
        Builder b = new Builder(loc("collect"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "transform"), b.block(b.return_(b.cast(3463, b.staticCall(3463, CpsDefaultGroovyMethods.class, "collect", b.localVariable("self"), b.new_(3463, ArrayList.class, b.functionCall(3463, b.localVariable("self"), "size")), b.localVariable("transform")), List.class, false))));
        throw new CpsCallableInvocation(f, null, self, transform);
    }

    public static<K, V, S, T >Map<Object, Object> collectEntries(Map<Object, Object> self, Map<Object, Object> collector, Closure<?> transform) {
        if ((!Caller.isAsynchronous(self, "collectEntries", collector, transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectEntries", self, collector, transform))) {
            return DefaultGroovyMethods.collectEntries(self, collector, transform);
        }
        Builder b = new Builder(loc("collectEntries"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "collector", "transform"), b.block(b.forInLoop(3488, null, java.util.Map.Entry.class, "entry", b.functionCall(3488, b.localVariable("self"), "entrySet"), b.block(b.staticCall(3489, CpsDefaultGroovyMethods.class, "addEntry", b.localVariable("collector"), b.staticCall(3489, CpsDefaultGroovyMethods.class, "callClosureForMapEntry", b.localVariable("transform"), b.localVariable("entry"))))), b.return_(b.localVariable("collector"))));
        throw new CpsCallableInvocation(f, null, self, collector, transform);
    }

    public static<K, V >Map<?, ?> collectEntries(Map<Object, Object> self, Closure<?> transform) {
        if ((!Caller.isAsynchronous(self, "collectEntries", transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectEntries", self, transform))) {
            return DefaultGroovyMethods.collectEntries(self, transform);
        }
        Builder b = new Builder(loc("collectEntries"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "transform"), b.block(b.return_(b.staticCall(3515, CpsDefaultGroovyMethods.class, "collectEntries", b.localVariable("self"), b.functionCall(3515, b.constant(DefaultGroovyMethodsSupport.class), "createSimilarMap", b.localVariable("self")), b.localVariable("transform")))));
        throw new CpsCallableInvocation(f, null, self, transform);
    }

    public static<K, V, E >Map<Object, Object> collectEntries(Iterator<Object> self, Closure<?> transform) {
        if ((!Caller.isAsynchronous(self, "collectEntries", transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectEntries", self, transform))) {
            return DefaultGroovyMethods.collectEntries(self, transform);
        }
        Builder b = new Builder(loc("collectEntries"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "transform"), b.block(b.return_(b.staticCall(3539, CpsDefaultGroovyMethods.class, "collectEntries", b.localVariable("self"), b.new_(3539, LinkedHashMap.class), b.localVariable("transform")))));
        throw new CpsCallableInvocation(f, null, self, transform);
    }

    public static<K, V, E >Map<Object, Object> collectEntries(Iterable<Object> self, Closure<?> transform) {
        if ((!Caller.isAsynchronous(self, "collectEntries", transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectEntries", self, transform))) {
            return DefaultGroovyMethods.collectEntries(self, transform);
        }
        Builder b = new Builder(loc("collectEntries"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "transform"), b.block(b.return_(b.staticCall(3564, CpsDefaultGroovyMethods.class, "collectEntries", b.functionCall(3564, b.localVariable("self"), "iterator"), b.localVariable("transform")))));
        throw new CpsCallableInvocation(f, null, self, transform);
    }

    public static<K, V, E >Map<Object, Object> collectEntries(Iterator<Object> self, Map<Object, Object> collector, Closure<?> transform) {
        if ((!Caller.isAsynchronous(self, "collectEntries", collector, transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectEntries", self, collector, transform))) {
            return DefaultGroovyMethods.collectEntries(self, collector, transform);
        }
        Builder b = new Builder(loc("collectEntries"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "collector", "transform"), b.block(b.while_(null, b.functionCall(3630, b.localVariable("self"), "hasNext"), b.block(b.declareVariable(3631, Object.class, "next", b.functionCall(3631, b.localVariable("self"), "next")), b.staticCall(3632, CpsDefaultGroovyMethods.class, "addEntry", b.localVariable("collector"), b.functionCall(3632, b.localVariable("transform"), "call", b.localVariable("next"))))), b.return_(b.localVariable("collector"))));
        throw new CpsCallableInvocation(f, null, self, collector, transform);
    }

    public static<K, V, E >Map<Object, Object> collectEntries(Iterable<Object> self, Map<Object, Object> collector, Closure<?> transform) {
        if ((!Caller.isAsynchronous(self, "collectEntries", collector, transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectEntries", self, collector, transform))) {
            return DefaultGroovyMethods.collectEntries(self, collector, transform);
        }
        Builder b = new Builder(loc("collectEntries"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "collector", "transform"), b.block(b.return_(b.staticCall(3661, CpsDefaultGroovyMethods.class, "collectEntries", b.functionCall(3661, b.localVariable("self"), "iterator"), b.localVariable("collector"), b.localVariable("transform")))));
        throw new CpsCallableInvocation(f, null, self, collector, transform);
    }

    public static<K, V, E >Map<Object, Object> collectEntries(Object[] self, Map<Object, Object> collector, Closure<?> transform) {
        if ((!Caller.isAsynchronous(self, "collectEntries", collector, transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectEntries", self, collector, transform))) {
            return DefaultGroovyMethods.collectEntries(self, collector, transform);
        }
        Builder b = new Builder(loc("collectEntries"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "collector", "transform"), b.block(b.return_(b.staticCall(3728, CpsDefaultGroovyMethods.class, "collectEntries", b.cast(3728, b.staticCall(3728, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), Iterable.class, false), b.localVariable("collector"), b.localVariable("transform")))));
        throw new CpsCallableInvocation(f, null, self, collector, transform);
    }

    public static<K, V, E >Map<Object, Object> collectEntries(Object[] self, Closure<?> transform) {
        if ((!Caller.isAsynchronous(self, "collectEntries", transform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "collectEntries", self, transform))) {
            return DefaultGroovyMethods.collectEntries(self, transform);
        }
        Builder b = new Builder(loc("collectEntries"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "transform"), b.block(b.return_(b.staticCall(3767, CpsDefaultGroovyMethods.class, "collectEntries", b.cast(3767, b.staticCall(3767, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), Iterable.class, false), b.new_(3767, LinkedHashMap.class), b.localVariable("transform")))));
        throw new CpsCallableInvocation(f, null, self, transform);
    }

    public static Object find(Object self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "find", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "find", self, closure))) {
            return DefaultGroovyMethods.find(self, closure);
        }
        Builder b = new Builder(loc("find"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(3808, BooleanClosureWrapper.class, "bcw", b.new_(3808, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forLoop(null, b.sequence(b.declareVariable(3809, Iterator.class, "iter", b.functionCall(3809, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self")))), b.functionCall(3809, b.localVariable("iter"), "hasNext"), b.sequence(), b.block(b.declareVariable(3810, Object.class, "value", b.functionCall(3810, b.localVariable("iter"), "next")), b.if_(b.functionCall(3811, b.localVariable("bcw"), "call", b.localVariable("value")), b.block(b.return_(b.localVariable("value")))))), b.return_(b.constant(null))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static Object findResult(Object self, Object defaultResult, Closure closure) {
        if ((!Caller.isAsynchronous(self, "findResult", defaultResult, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findResult", self, defaultResult, closure))) {
            return DefaultGroovyMethods.findResult(self, defaultResult, closure);
        }
        Builder b = new Builder(loc("findResult"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "defaultResult", "closure"), b.block(b.declareVariable(3846, Object.class, "result", b.staticCall(3846, CpsDefaultGroovyMethods.class, "findResult", b.localVariable("self"), b.localVariable("closure"))), b.if_(b.compareEqual(3847, b.localVariable("result"), b.constant(null)), b.return_(b.localVariable("defaultResult"))), b.return_(b.localVariable("result"))));
        throw new CpsCallableInvocation(f, null, self, defaultResult, closure);
    }

    public static Object findResult(Object self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "findResult", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findResult", self, closure))) {
            return DefaultGroovyMethods.findResult(self, closure);
        }
        Builder b = new Builder(loc("findResult"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.forLoop(null, b.sequence(b.declareVariable(3860, Iterator.class, "iter", b.functionCall(3860, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self")))), b.functionCall(3860, b.localVariable("iter"), "hasNext"), b.sequence(), b.block(b.declareVariable(3861, Object.class, "value", b.functionCall(3861, b.localVariable("iter"), "next")), b.declareVariable(3862, Object.class, "result", b.functionCall(3862, b.localVariable("closure"), "call", b.localVariable("value"))), b.if_(b.compareNotEqual(3863, b.localVariable("result"), b.constant(null)), b.block(b.return_(b.localVariable("result")))))), b.return_(b.constant(null))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Object find(Collection<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "find", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "find", self, closure))) {
            return DefaultGroovyMethods.find(self, closure);
        }
        Builder b = new Builder(loc("find"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(3882, BooleanClosureWrapper.class, "bcw", b.new_(3882, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forInLoop(3883, null, Object.class, "value", b.localVariable("self"), b.block(b.if_(b.functionCall(3884, b.localVariable("bcw"), "call", b.localVariable("value")), b.block(b.return_(b.localVariable("value")))))), b.return_(b.constant(null))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Object find(Object[] self, Closure condition) {
        if ((!Caller.isAsynchronous(self, "find", condition))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "find", self, condition))) {
            return DefaultGroovyMethods.find(self, condition);
        }
        Builder b = new Builder(loc("find"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "condition"), b.block(b.declareVariable(3906, BooleanClosureWrapper.class, "bcw", b.new_(3906, BooleanClosureWrapper.class, b.localVariable("condition"))), b.forInLoop(3907, null, Object.class, "element", b.localVariable("self"), b.block(b.if_(b.functionCall(3908, b.localVariable("bcw"), "call", b.localVariable("element")), b.block(b.return_(b.localVariable("element")))))), b.return_(b.constant(null))));
        throw new CpsCallableInvocation(f, null, self, condition);
    }

    public static<T, U, V, E >Object findResult(Collection<Object> self, Object defaultResult, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "findResult", defaultResult, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findResult", self, defaultResult, closure))) {
            return DefaultGroovyMethods.findResult(self, defaultResult, closure);
        }
        Builder b = new Builder(loc("findResult"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "defaultResult", "closure"), b.block(b.declareVariable(3951, Object.class, "result", b.staticCall(3951, CpsDefaultGroovyMethods.class, "findResult", b.localVariable("self"), b.localVariable("closure"))), b.if_(b.compareEqual(3952, b.localVariable("result"), b.constant(null)), b.return_(b.localVariable("defaultResult"))), b.return_(b.localVariable("result"))));
        throw new CpsCallableInvocation(f, null, self, defaultResult, closure);
    }

    public static<T, U >Object findResult(Collection<Object> self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "findResult", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findResult", self, closure))) {
            return DefaultGroovyMethods.findResult(self, closure);
        }
        Builder b = new Builder(loc("findResult"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.forInLoop(3972, null, Object.class, "value", b.localVariable("self"), b.block(b.declareVariable(3973, Object.class, "result", b.functionCall(3973, b.localVariable("closure"), "call", b.localVariable("value"))), b.if_(b.compareNotEqual(3974, b.localVariable("result"), b.constant(null)), b.block(b.return_(b.localVariable("result")))))), b.return_(b.constant(null))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T, U >Collection<Object> findResults(Iterable<Object> self, Closure<Object> filteringTransform) {
        if ((!Caller.isAsynchronous(self, "findResults", filteringTransform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findResults", self, filteringTransform))) {
            return DefaultGroovyMethods.findResults(self, filteringTransform);
        }
        Builder b = new Builder(loc("findResults"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "filteringTransform"), b.block(b.declareVariable(4008, List.class, "result", b.new_(4008, ArrayList.class)), b.forInLoop(4009, null, Object.class, "value", b.localVariable("self"), b.block(b.declareVariable(4010, Object.class, "transformed", b.functionCall(4010, b.localVariable("filteringTransform"), "call", b.localVariable("value"))), b.if_(b.compareNotEqual(4011, b.localVariable("transformed"), b.constant(null)), b.block(b.functionCall(4012, b.localVariable("result"), "add", b.localVariable("transformed")))))), b.return_(b.localVariable("result"))));
        throw new CpsCallableInvocation(f, null, self, filteringTransform);
    }

    public static<T, K, V >Collection<Object> findResults(Map<Object, Object> self, Closure<Object> filteringTransform) {
        if ((!Caller.isAsynchronous(self, "findResults", filteringTransform))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findResults", self, filteringTransform))) {
            return DefaultGroovyMethods.findResults(self, filteringTransform);
        }
        Builder b = new Builder(loc("findResults"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "filteringTransform"), b.block(b.declareVariable(4037, List.class, "result", b.new_(4037, ArrayList.class)), b.forInLoop(4038, null, java.util.Map.Entry.class, "entry", b.functionCall(4038, b.localVariable("self"), "entrySet"), b.block(b.declareVariable(4039, Object.class, "transformed", b.staticCall(4039, CpsDefaultGroovyMethods.class, "callClosureForMapEntry", b.localVariable("filteringTransform"), b.localVariable("entry"))), b.if_(b.compareNotEqual(4040, b.localVariable("transformed"), b.constant(null)), b.block(b.functionCall(4041, b.localVariable("result"), "add", b.localVariable("transformed")))))), b.return_(b.localVariable("result"))));
        throw new CpsCallableInvocation(f, null, self, filteringTransform);
    }

    public static<K, V >java.util.Map.Entry<Object, Object> find(Map<Object, Object> self, Closure<?> closure) {
        if ((!Caller.isAsynchronous(self, "find", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "find", self, closure))) {
            return DefaultGroovyMethods.find(self, closure);
        }
        Builder b = new Builder(loc("find"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(4059, BooleanClosureWrapper.class, "bcw", b.new_(4059, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forInLoop(4060, null, java.util.Map.Entry.class, "entry", b.functionCall(4060, b.localVariable("self"), "entrySet"), b.block(b.if_(b.functionCall(4061, b.localVariable("bcw"), "callForMap", b.localVariable("entry")), b.block(b.return_(b.localVariable("entry")))))), b.return_(b.constant(null))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T, U, V, A, B >Object findResult(Map<Object, Object> self, Object defaultResult, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "findResult", defaultResult, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findResult", self, defaultResult, closure))) {
            return DefaultGroovyMethods.findResult(self, defaultResult, closure);
        }
        Builder b = new Builder(loc("findResult"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "defaultResult", "closure"), b.block(b.declareVariable(4085, Object.class, "result", b.staticCall(4085, CpsDefaultGroovyMethods.class, "findResult", b.localVariable("self"), b.localVariable("closure"))), b.if_(b.compareEqual(4086, b.localVariable("result"), b.constant(null)), b.return_(b.localVariable("defaultResult"))), b.return_(b.localVariable("result"))));
        throw new CpsCallableInvocation(f, null, self, defaultResult, closure);
    }

    public static<T, K, V >Object findResult(Map<Object, Object> self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "findResult", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findResult", self, closure))) {
            return DefaultGroovyMethods.findResult(self, closure);
        }
        Builder b = new Builder(loc("findResult"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.forInLoop(4106, null, java.util.Map.Entry.class, "entry", b.functionCall(4106, b.localVariable("self"), "entrySet"), b.block(b.declareVariable(4107, Object.class, "result", b.staticCall(4107, CpsDefaultGroovyMethods.class, "callClosureForMapEntry", b.localVariable("closure"), b.localVariable("entry"))), b.if_(b.compareNotEqual(4108, b.localVariable("result"), b.constant(null)), b.block(b.return_(b.localVariable("result")))))), b.return_(b.constant(null))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Set<Object> findAll(Set<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "findAll", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findAll", self, closure))) {
            return DefaultGroovyMethods.findAll(self, closure);
        }
        Builder b = new Builder(loc("findAll"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.cast(4125, b.staticCall(4125, CpsDefaultGroovyMethods.class, "findAll", b.cast(4125, b.localVariable("self"), Collection.class, false), b.localVariable("closure")), Set.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >List<Object> findAll(List<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "findAll", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findAll", self, closure))) {
            return DefaultGroovyMethods.findAll(self, closure);
        }
        Builder b = new Builder(loc("findAll"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.cast(4138, b.staticCall(4138, CpsDefaultGroovyMethods.class, "findAll", b.cast(4138, b.localVariable("self"), Collection.class, false), b.localVariable("closure")), List.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Collection<Object> findAll(Collection<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "findAll", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findAll", self, closure))) {
            return DefaultGroovyMethods.findAll(self, closure);
        }
        Builder b = new Builder(loc("findAll"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(4151, Collection.class, "answer", b.functionCall(4151, b.constant(DefaultGroovyMethodsSupport.class), "createSimilarCollection", b.localVariable("self"))), b.declareVariable(4152, Iterator.class, "iter", b.functionCall(4152, b.localVariable("self"), "iterator")), b.return_(b.staticCall(4153, CpsDefaultGroovyMethods.class, "findAll", b.localVariable("closure"), b.localVariable("answer"), b.localVariable("iter")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Collection<Object> findAll(Object[] self, Closure condition) {
        if ((!Caller.isAsynchronous(self, "findAll", condition))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findAll", self, condition))) {
            return DefaultGroovyMethods.findAll(self, condition);
        }
        Builder b = new Builder(loc("findAll"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "condition"), b.block(b.declareVariable(4169, Collection.class, "answer", b.new_(4169, ArrayList.class)), b.return_(b.staticCall(4170, CpsDefaultGroovyMethods.class, "findAll", b.localVariable("condition"), b.localVariable("answer"), b.new_(4170, ArrayIterator.class, b.localVariable("self"))))));
        throw new CpsCallableInvocation(f, null, self, condition);
    }

    public static Collection findAll(Object self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "findAll", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findAll", self, closure))) {
            return DefaultGroovyMethods.findAll(self, closure);
        }
        Builder b = new Builder(loc("findAll"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(4254, List.class, "answer", b.new_(4254, ArrayList.class)), b.declareVariable(4255, Iterator.class, "iter", b.functionCall(4255, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self"))), b.return_(b.staticCall(4256, CpsDefaultGroovyMethods.class, "findAll", b.localVariable("closure"), b.localVariable("answer"), b.localVariable("iter")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >boolean retainAll(Collection<Object> self, Closure condition) {
        if ((!Caller.isAsynchronous(self, "retainAll", condition))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "retainAll", self, condition))) {
            return DefaultGroovyMethods.retainAll(self, condition);
        }
        Builder b = new Builder(loc("retainAll"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "condition"), b.block(b.declareVariable(4385, Iterator.class, "iter", b.functionCall(4385, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self"))), b.declareVariable(4386, BooleanClosureWrapper.class, "bcw", b.new_(4386, BooleanClosureWrapper.class, b.localVariable("condition"))), b.declareVariable(4387, int.class, "result", b.constant(false)), b.while_(null, b.functionCall(4388, b.localVariable("iter"), "hasNext"), b.block(b.declareVariable(4389, Object.class, "value", b.functionCall(4389, b.localVariable("iter"), "next")), b.if_(b.not(4390, b.functionCall(4390, b.localVariable("bcw"), "call", b.localVariable("value"))), b.block(b.functionCall(4391, b.localVariable("iter"), "remove"), b.assign(4392, b.localVariable("result"), b.constant(true)))))), b.return_(b.localVariable("result"))));
        throw new CpsCallableInvocation(f, null, self, condition);
    }

    public static<T >boolean removeAll(Collection<Object> self, Closure condition) {
        if ((!Caller.isAsynchronous(self, "removeAll", condition))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "removeAll", self, condition))) {
            return DefaultGroovyMethods.removeAll(self, condition);
        }
        Builder b = new Builder(loc("removeAll"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "condition"), b.block(b.declareVariable(4412, Iterator.class, "iter", b.functionCall(4412, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self"))), b.declareVariable(4413, BooleanClosureWrapper.class, "bcw", b.new_(4413, BooleanClosureWrapper.class, b.localVariable("condition"))), b.declareVariable(4414, int.class, "result", b.constant(false)), b.while_(null, b.functionCall(4415, b.localVariable("iter"), "hasNext"), b.block(b.declareVariable(4416, Object.class, "value", b.functionCall(4416, b.localVariable("iter"), "next")), b.if_(b.functionCall(4417, b.localVariable("bcw"), "call", b.localVariable("value")), b.block(b.functionCall(4418, b.localVariable("iter"), "remove"), b.assign(4419, b.localVariable("result"), b.constant(true)))))), b.return_(b.localVariable("result"))));
        throw new CpsCallableInvocation(f, null, self, condition);
    }

    public static Collection split(Object self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "split", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "split", self, closure))) {
            return DefaultGroovyMethods.split(self, closure);
        }
        Builder b = new Builder(loc("split"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(4478, List.class, "accept", b.new_(4478, ArrayList.class)), b.declareVariable(4479, List.class, "reject", b.new_(4479, ArrayList.class)), b.return_(b.staticCall(4480, CpsDefaultGroovyMethods.class, "split", b.localVariable("closure"), b.localVariable("accept"), b.localVariable("reject"), b.functionCall(4480, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self"))))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Collection<Collection<Object>> split(Collection<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "split", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "split", self, closure))) {
            return DefaultGroovyMethods.split(self, closure);
        }
        Builder b = new Builder(loc("split"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(4497, Collection.class, "accept", b.functionCall(4497, b.constant(DefaultGroovyMethodsSupport.class), "createSimilarCollection", b.localVariable("self"))), b.declareVariable(4498, Collection.class, "reject", b.functionCall(4498, b.constant(DefaultGroovyMethodsSupport.class), "createSimilarCollection", b.localVariable("self"))), b.declareVariable(4499, Iterator.class, "iter", b.functionCall(4499, b.localVariable("self"), "iterator")), b.return_(b.staticCall(4500, CpsDefaultGroovyMethods.class, "split", b.localVariable("closure"), b.localVariable("accept"), b.localVariable("reject"), b.localVariable("iter")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >List<List<Object>> split(List<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "split", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "split", self, closure))) {
            return DefaultGroovyMethods.split(self, closure);
        }
        Builder b = new Builder(loc("split"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.cast(4534, b.cast(4534, b.staticCall(4534, CpsDefaultGroovyMethods.class, "split", b.cast(4534, b.localVariable("self"), Collection.class, false), b.localVariable("closure")), List.class, false), List.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >List<Set<Object>> split(Set<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "split", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "split", self, closure))) {
            return DefaultGroovyMethods.split(self, closure);
        }
        Builder b = new Builder(loc("split"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.cast(4552, b.cast(4552, b.staticCall(4552, CpsDefaultGroovyMethods.class, "split", b.cast(4552, b.localVariable("self"), Collection.class, false), b.localVariable("closure")), List.class, false), List.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static List combinations(Iterable self, Closure<?> function) {
        if ((!Caller.isAsynchronous(self, "combinations", function))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "combinations", self, function))) {
            return DefaultGroovyMethods.combinations(self, function);
        }
        Builder b = new Builder(loc("combinations"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "function"), b.block(b.return_(b.staticCall(4596, CpsDefaultGroovyMethods.class, "collect", b.functionCall(4596, b.constant(GroovyCollections.class), "combinations", b.localVariable("self")), b.localVariable("function")))));
        throw new CpsCallableInvocation(f, null, self, function);
    }

    public static void eachCombination(Iterable self, Closure<?> function) {
        if ((!Caller.isAsynchronous(self, "eachCombination", function))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "eachCombination", self, function))) {
            DefaultGroovyMethods.eachCombination(self, function);
            return ;
        }
        Builder b = new Builder(loc("eachCombination"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "function"), b.block(b.staticCall(4611, CpsDefaultGroovyMethods.class, "each", b.functionCall(4611, b.constant(GroovyCollections.class), "combinations", b.localVariable("self")), b.localVariable("function"))));
        throw new CpsCallableInvocation(f, null, self, function);
    }

    public static<T, V >List<Object> permutations(Iterable<Object> self, Closure<Object> function) {
        if ((!Caller.isAsynchronous(self, "permutations", function))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "permutations", self, function))) {
            return DefaultGroovyMethods.permutations(self, function);
        }
        Builder b = new Builder(loc("permutations"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "function"), b.block(b.return_(b.staticCall(4673, CpsDefaultGroovyMethods.class, "collect", b.staticCall(4673, CpsDefaultGroovyMethods.class, "permutations", b.localVariable("self")), b.localVariable("function")))));
        throw new CpsCallableInvocation(f, null, self, function);
    }

    public static<T >Iterator<List<Object>> eachPermutation(Iterable<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "eachPermutation", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "eachPermutation", self, closure))) {
            return DefaultGroovyMethods.eachPermutation(self, closure);
        }
        Builder b = new Builder(loc("eachPermutation"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(4710, Iterator.class, "generator", b.new_(4710, PermutationGenerator.class, b.localVariable("self"))), b.while_(null, b.functionCall(4711, b.localVariable("generator"), "hasNext"), b.block(b.functionCall(4712, b.localVariable("closure"), "call", b.functionCall(4712, b.localVariable("generator"), "next")))), b.return_(b.localVariable("generator"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, V >Map<Object, Object> findAll(Map<Object, Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "findAll", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findAll", self, closure))) {
            return DefaultGroovyMethods.findAll(self, closure);
        }
        Builder b = new Builder(loc("findAll"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(4762, Map.class, "answer", b.functionCall(4762, b.constant(DefaultGroovyMethodsSupport.class), "createSimilarMap", b.localVariable("self"))), b.declareVariable(4763, BooleanClosureWrapper.class, "bcw", b.new_(4763, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forInLoop(4764, null, java.util.Map.Entry.class, "entry", b.functionCall(4764, b.localVariable("self"), "entrySet"), b.block(b.if_(b.functionCall(4765, b.localVariable("bcw"), "callForMap", b.localVariable("entry")), b.block(b.functionCall(4766, b.localVariable("answer"), "put", b.functionCall(4766, b.localVariable("entry"), "getKey"), b.functionCall(4766, b.localVariable("entry"), "getValue")))))), b.return_(b.localVariable("answer"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, T >Map<Object, List<Object>> groupBy(Iterable<Object> self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "groupBy", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "groupBy", self, closure))) {
            return DefaultGroovyMethods.groupBy(self, closure);
        }
        Builder b = new Builder(loc("groupBy"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(4799, Map.class, "answer", b.new_(4799, LinkedHashMap.class)), b.forInLoop(4800, null, Object.class, "element", b.localVariable("self"), b.block(b.declareVariable(4801, Object.class, "value", b.functionCall(4801, b.localVariable("closure"), "call", b.localVariable("element"))), b.staticCall(4802, CpsDefaultGroovyMethods.class, "groupAnswer", b.localVariable("answer"), b.localVariable("element"), b.localVariable("value")))), b.return_(b.localVariable("answer"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, T >Map<Object, List<Object>> groupBy(Object[] self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "groupBy", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "groupBy", self, closure))) {
            return DefaultGroovyMethods.groupBy(self, closure);
        }
        Builder b = new Builder(loc("groupBy"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(4826, CpsDefaultGroovyMethods.class, "groupBy", b.cast(4826, b.functionCall(4826, b.constant(Arrays.class), "asList", b.localVariable("self")), Iterable.class, false), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, E >Map<Object, Integer> countBy(Iterable<Object> self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "countBy", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "countBy", self, closure))) {
            return DefaultGroovyMethods.countBy(self, closure);
        }
        Builder b = new Builder(loc("countBy"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(4993, CpsDefaultGroovyMethods.class, "countBy", b.functionCall(4993, b.localVariable("self"), "iterator"), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, E >Map<Object, Integer> countBy(Object[] self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "countBy", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "countBy", self, closure))) {
            return DefaultGroovyMethods.countBy(self, closure);
        }
        Builder b = new Builder(loc("countBy"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(5013, CpsDefaultGroovyMethods.class, "countBy", b.cast(5013, b.functionCall(5013, b.constant(Arrays.class), "asList", b.localVariable("self")), Iterable.class, false), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, E >Map<Object, Integer> countBy(Iterator<Object> self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "countBy", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "countBy", self, closure))) {
            return DefaultGroovyMethods.countBy(self, closure);
        }
        Builder b = new Builder(loc("countBy"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(5033, Map.class, "answer", b.new_(5033, LinkedHashMap.class)), b.while_(null, b.functionCall(5034, b.localVariable("self"), "hasNext"), b.block(b.declareVariable(5035, Object.class, "value", b.functionCall(5035, b.localVariable("closure"), "call", b.functionCall(5035, b.localVariable("self"), "next"))), b.staticCall(5036, CpsDefaultGroovyMethods.class, "countAnswer", b.localVariable("answer"), b.localVariable("value")))), b.return_(b.localVariable("answer"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<G, K, V >Map<Object, List<java.util.Map.Entry<Object, Object>>> groupEntriesBy(Map<Object, Object> self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "groupEntriesBy", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "groupEntriesBy", self, closure))) {
            return DefaultGroovyMethods.groupEntriesBy(self, closure);
        }
        Builder b = new Builder(loc("groupEntriesBy"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(5060, Map.class, "answer", b.new_(5060, LinkedHashMap.class)), b.forInLoop(5061, null, java.util.Map.Entry.class, "entry", b.functionCall(5061, b.localVariable("self"), "entrySet"), b.block(b.declareVariable(5062, Object.class, "value", b.staticCall(5062, CpsDefaultGroovyMethods.class, "callClosureForMapEntry", b.localVariable("closure"), b.localVariable("entry"))), b.staticCall(5063, CpsDefaultGroovyMethods.class, "groupAnswer", b.localVariable("answer"), b.localVariable("entry"), b.localVariable("value")))), b.return_(b.localVariable("answer"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<G, K, V >Map<Object, Map<Object, Object>> groupBy(Map<Object, Object> self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "groupBy", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "groupBy", self, closure))) {
            return DefaultGroovyMethods.groupBy(self, closure);
        }
        Builder b = new Builder(loc("groupBy"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(5090, Map.class, "initial", b.staticCall(5090, CpsDefaultGroovyMethods.class, "groupEntriesBy", b.localVariable("self"), b.localVariable("closure"))), b.declareVariable(5091, Map.class, "answer", b.new_(5091, LinkedHashMap.class)), b.forInLoop(5092, null, java.util.Map.Entry.class, "outer", b.functionCall(5092, b.localVariable("initial"), "entrySet"), b.block(b.declareVariable(5093, Object.class, "key", b.functionCall(5093, b.localVariable("outer"), "getKey")), b.declareVariable(5094, List.class, "entries", b.functionCall(5094, b.localVariable("outer"), "getValue")), b.declareVariable(5095, Map.class, "target", b.functionCall(5095, b.constant(DefaultGroovyMethodsSupport.class), "createSimilarMap", b.localVariable("self"))), b.staticCall(5096, CpsDefaultGroovyMethods.class, "putAll", b.localVariable("target"), b.localVariable("entries")), b.functionCall(5097, b.localVariable("answer"), "put", b.localVariable("key"), b.localVariable("target")))), b.return_(b.localVariable("answer"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, U, V >Map<Object, Integer> countBy(Map<Object, Object> self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "countBy", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "countBy", self, closure))) {
            return DefaultGroovyMethods.countBy(self, closure);
        }
        Builder b = new Builder(loc("countBy"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(5190, Map.class, "answer", b.new_(5190, LinkedHashMap.class)), b.forInLoop(5191, null, Object.class, "entry", b.functionCall(5191, b.localVariable("self"), "entrySet"), b.block(b.staticCall(5192, CpsDefaultGroovyMethods.class, "countAnswer", b.localVariable("answer"), b.staticCall(5192, CpsDefaultGroovyMethods.class, "callClosureForMapEntry", b.localVariable("closure"), b.cast(5192, b.localVariable("entry"), java.util.Map.Entry.class, false))))), b.return_(b.localVariable("answer"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T, V >Object inject(Collection<Object> self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "inject", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "inject", self, closure))) {
            return DefaultGroovyMethods.inject(self, closure);
        }
        Builder b = new Builder(loc("inject"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.if_(b.functionCall(5269, b.localVariable("self"), "isEmpty"), b.block(b.throw_(5270, b.new_(5270, NoSuchElementException.class, b.constant("Cannot call inject() on an empty collection without passing an initial value."))))), b.declareVariable(5272, Iterator.class, "iter", b.functionCall(5272, b.localVariable("self"), "iterator")), b.declareVariable(5273, Object.class, "head", b.functionCall(5273, b.localVariable("iter"), "next")), b.declareVariable(5274, Collection.class, "tail", b.staticCall(5274, CpsDefaultGroovyMethods.class, "tail", b.localVariable("self"))), b.if_(b.not(5275, b.functionCall(5275, b.functionCall(5275, b.localVariable("tail"), "iterator"), "hasNext")), b.block(b.return_(b.localVariable("head")))), b.return_(b.cast(5279, b.staticCall(5279, CpsDefaultGroovyMethods.class, "inject", b.cast(5279, b.localVariable("tail"), Collection.class, false), b.localVariable("head"), b.localVariable("closure")), Object.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<E, T, U, V >Object inject(Collection<Object> self, Object initialValue, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "inject", initialValue, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "inject", self, initialValue, closure))) {
            return DefaultGroovyMethods.inject(self, initialValue, closure);
        }
        Builder b = new Builder(loc("inject"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "initialValue", "closure"), b.block(b.return_(b.cast(5324, b.staticCall(5324, CpsDefaultGroovyMethods.class, "inject", b.cast(5324, b.functionCall(5324, b.localVariable("self"), "iterator"), Iterator.class, false), b.localVariable("initialValue"), b.localVariable("closure")), Object.class, false))));
        throw new CpsCallableInvocation(f, null, self, initialValue, closure);
    }

    public static<K, V, T, U, W >Object inject(Map<Object, Object> self, Object initialValue, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "inject", initialValue, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "inject", self, initialValue, closure))) {
            return DefaultGroovyMethods.inject(self, initialValue, closure);
        }
        Builder b = new Builder(loc("inject"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "initialValue", "closure"), b.block(b.declareVariable(5350, Object.class, "value", b.localVariable("initialValue")), b.forInLoop(5351, null, java.util.Map.Entry.class, "entry", b.functionCall(5351, b.localVariable("self"), "entrySet"), b.block(b.if_(b.compareEqual(5352, b.functionCall(5352, b.localVariable("closure"), "getMaximumNumberOfParameters"), b.constant(3)), b.block(b.assign(5353, b.localVariable("value"), b.functionCall(5353, b.localVariable("closure"), "call", b.localVariable("value"), b.functionCall(5353, b.localVariable("entry"), "getKey"), b.functionCall(5353, b.localVariable("entry"), "getValue")))), b.block(b.assign(5355, b.localVariable("value"), b.functionCall(5355, b.localVariable("closure"), "call", b.localVariable("value"), b.localVariable("entry"))))))), b.return_(b.localVariable("value"))));
        throw new CpsCallableInvocation(f, null, self, initialValue, closure);
    }

    public static<E, T, U, V >Object inject(Iterator<Object> self, Object initialValue, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "inject", initialValue, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "inject", self, initialValue, closure))) {
            return DefaultGroovyMethods.inject(self, initialValue, closure);
        }
        Builder b = new Builder(loc("inject"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "initialValue", "closure"), b.block(b.declareVariable(5377, Object.class, "value", b.localVariable("initialValue")), b.declareVariable(5378, Object[].class, "params", b.newArray(5378, Object.class, b.constant(2))), b.while_(null, b.functionCall(5379, b.localVariable("self"), "hasNext"), b.block(b.declareVariable(5380, Object.class, "item", b.functionCall(5380, b.localVariable("self"), "next")), b.assign(5381, b.array(5381, b.localVariable("params"), b.constant(0)), b.localVariable("value")), b.assign(5382, b.array(5382, b.localVariable("params"), b.constant(1)), b.localVariable("item")), b.assign(5383, b.localVariable("value"), b.functionCall(5383, b.localVariable("closure"), "call", b.localVariable("params"))))), b.return_(b.localVariable("value"))));
        throw new CpsCallableInvocation(f, null, self, initialValue, closure);
    }

    public static<T, V >Object inject(Object self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "inject", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "inject", self, closure))) {
            return DefaultGroovyMethods.inject(self, closure);
        }
        Builder b = new Builder(loc("inject"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(5403, Iterator.class, "iter", b.functionCall(5403, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self"))), b.if_(b.not(5404, b.functionCall(5404, b.localVariable("iter"), "hasNext")), b.block(b.throw_(5405, b.new_(5405, NoSuchElementException.class, b.constant("Cannot call inject() over an empty iterable without passing an initial value."))))), b.declareVariable(5407, Object.class, "initialValue", b.functionCall(5407, b.localVariable("iter"), "next")), b.return_(b.cast(5408, b.staticCall(5408, CpsDefaultGroovyMethods.class, "inject", b.localVariable("iter"), b.localVariable("initialValue"), b.localVariable("closure")), Object.class, false))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T, U, V >Object inject(Object self, Object initialValue, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "inject", initialValue, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "inject", self, initialValue, closure))) {
            return DefaultGroovyMethods.inject(self, initialValue, closure);
        }
        Builder b = new Builder(loc("inject"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "initialValue", "closure"), b.block(b.declareVariable(5426, Iterator.class, "iter", b.functionCall(5426, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self"))), b.return_(b.cast(5427, b.staticCall(5427, CpsDefaultGroovyMethods.class, "inject", b.localVariable("iter"), b.localVariable("initialValue"), b.localVariable("closure")), Object.class, false))));
        throw new CpsCallableInvocation(f, null, self, initialValue, closure);
    }

    public static<E, T, V >Object inject(Object[] self, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "inject", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "inject", self, closure))) {
            return DefaultGroovyMethods.inject(self, closure);
        }
        Builder b = new Builder(loc("inject"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(5443, CpsDefaultGroovyMethods.class, "inject", b.cast(5443, b.localVariable("self"), Object.class, false), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<E, T, U, V >Object inject(Object[] self, Object initialValue, Closure<Object> closure) {
        if ((!Caller.isAsynchronous(self, "inject", initialValue, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "inject", self, initialValue, closure))) {
            return DefaultGroovyMethods.inject(self, initialValue, closure);
        }
        Builder b = new Builder(loc("inject"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "initialValue", "closure"), b.block(b.declareVariable(5461, Object[].class, "params", b.newArray(5461, Object.class, b.constant(2))), b.declareVariable(5462, Object.class, "value", b.localVariable("initialValue")), b.forInLoop(5463, null, Object.class, "next", b.localVariable("self"), b.block(b.assign(5464, b.array(5464, b.localVariable("params"), b.constant(0)), b.localVariable("value")), b.assign(5465, b.array(5465, b.localVariable("params"), b.constant(1)), b.localVariable("next")), b.assign(5466, b.localVariable("value"), b.functionCall(5466, b.localVariable("closure"), "call", b.localVariable("params"))))), b.return_(b.localVariable("value"))));
        throw new CpsCallableInvocation(f, null, self, initialValue, closure);
    }

    public static Object sum(Iterable self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "sum", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "sum", self, closure))) {
            return DefaultGroovyMethods.sum(self, closure);
        }
        Builder b = new Builder(loc("sum"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(5813, CpsDefaultGroovyMethods.class, "sum", b.localVariable("self"), b.constant(null), b.localVariable("closure"), b.constant(true)))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static Object sum(Object[] self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "sum", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "sum", self, closure))) {
            return DefaultGroovyMethods.sum(self, closure);
        }
        Builder b = new Builder(loc("sum"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(5828, CpsDefaultGroovyMethods.class, "sum", b.staticCall(5828, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), b.constant(null), b.localVariable("closure"), b.constant(true)))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static Object sum(Iterator<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "sum", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "sum", self, closure))) {
            return DefaultGroovyMethods.sum(self, closure);
        }
        Builder b = new Builder(loc("sum"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(5844, CpsDefaultGroovyMethods.class, "sum", b.staticCall(5844, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), b.constant(null), b.localVariable("closure"), b.constant(true)))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static Object sum(Iterable self, Object initialValue, Closure closure) {
        if ((!Caller.isAsynchronous(self, "sum", initialValue, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "sum", self, initialValue, closure))) {
            return DefaultGroovyMethods.sum(self, initialValue, closure);
        }
        Builder b = new Builder(loc("sum"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "initialValue", "closure"), b.block(b.return_(b.staticCall(5871, CpsDefaultGroovyMethods.class, "sum", b.localVariable("self"), b.localVariable("initialValue"), b.localVariable("closure"), b.constant(false)))));
        throw new CpsCallableInvocation(f, null, self, initialValue, closure);
    }

    public static Object sum(Object[] self, Object initialValue, Closure closure) {
        if ((!Caller.isAsynchronous(self, "sum", initialValue, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "sum", self, initialValue, closure))) {
            return DefaultGroovyMethods.sum(self, initialValue, closure);
        }
        Builder b = new Builder(loc("sum"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "initialValue", "closure"), b.block(b.return_(b.staticCall(5887, CpsDefaultGroovyMethods.class, "sum", b.staticCall(5887, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), b.localVariable("initialValue"), b.localVariable("closure"), b.constant(false)))));
        throw new CpsCallableInvocation(f, null, self, initialValue, closure);
    }

    public static Object sum(Iterator<Object> self, Object initialValue, Closure closure) {
        if ((!Caller.isAsynchronous(self, "sum", initialValue, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "sum", self, initialValue, closure))) {
            return DefaultGroovyMethods.sum(self, initialValue, closure);
        }
        Builder b = new Builder(loc("sum"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "initialValue", "closure"), b.block(b.return_(b.staticCall(5904, CpsDefaultGroovyMethods.class, "sum", b.staticCall(5904, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), b.localVariable("initialValue"), b.localVariable("closure"), b.constant(false)))));
        throw new CpsCallableInvocation(f, null, self, initialValue, closure);
    }

    public static<T >Object min(Iterable<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "min", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "min", self, closure))) {
            return DefaultGroovyMethods.min(self, closure);
        }
        Builder b = new Builder(loc("min"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(6370, int.class, "params", b.functionCall(6370, b.localVariable("closure"), "getMaximumNumberOfParameters")), b.if_(b.compareNotEqual(6371, b.localVariable("params"), b.constant(1)), b.block(b.return_(b.staticCall(6372, CpsDefaultGroovyMethods.class, "min", b.localVariable("self"), b.new_(6372, ClosureComparator.class, b.localVariable("closure")))))), b.declareVariable(6374, int.class, "first", b.constant(true)), b.declareVariable(6375, Object.class, "answer", b.constant(null)), b.declareVariable(6376, Object.class, "answerValue", b.constant(null)), b.forInLoop(6377, null, Object.class, "item", b.localVariable("self"), b.block(b.declareVariable(6378, Object.class, "value", b.functionCall(6378, b.localVariable("closure"), "call", b.localVariable("item"))), b.if_(b.localVariable("first"), b.block(b.assign(6380, b.localVariable("first"), b.constant(false)), b.assign(6381, b.localVariable("answer"), b.localVariable("item")), b.assign(6382, b.localVariable("answerValue"), b.localVariable("value"))), b.if_(b.functionCall(6383, b.constant(ScriptBytecodeAdapter.class), "compareLessThan", b.localVariable("value"), b.localVariable("answerValue")), b.block(b.assign(6384, b.localVariable("answer"), b.localVariable("item")), b.assign(6385, b.localVariable("answerValue"), b.localVariable("value"))))))), b.return_(b.localVariable("answer"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, V >java.util.Map.Entry<Object, Object> min(Map<Object, Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "min", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "min", self, closure))) {
            return DefaultGroovyMethods.min(self, closure);
        }
        Builder b = new Builder(loc("min"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(6426, CpsDefaultGroovyMethods.class, "min", b.cast(6426, b.functionCall(6426, b.localVariable("self"), "entrySet"), Iterable.class, false), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, V >java.util.Map.Entry<Object, Object> max(Map<Object, Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "max", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "max", self, closure))) {
            return DefaultGroovyMethods.max(self, closure);
        }
        Builder b = new Builder(loc("max"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(6464, CpsDefaultGroovyMethods.class, "max", b.cast(6464, b.functionCall(6464, b.localVariable("self"), "entrySet"), Iterable.class, false), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Object min(Iterator<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "min", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "min", self, closure))) {
            return DefaultGroovyMethods.min(self, closure);
        }
        Builder b = new Builder(loc("min"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(6489, CpsDefaultGroovyMethods.class, "min", b.cast(6489, b.staticCall(6489, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), Iterable.class, false), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Object min(Object[] self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "min", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "min", self, closure))) {
            return DefaultGroovyMethods.min(self, closure);
        }
        Builder b = new Builder(loc("min"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(6512, CpsDefaultGroovyMethods.class, "min", b.cast(6512, b.staticCall(6512, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), Iterable.class, false), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Object max(Iterable<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "max", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "max", self, closure))) {
            return DefaultGroovyMethods.max(self, closure);
        }
        Builder b = new Builder(loc("max"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(6603, int.class, "params", b.functionCall(6603, b.localVariable("closure"), "getMaximumNumberOfParameters")), b.if_(b.compareNotEqual(6604, b.localVariable("params"), b.constant(1)), b.block(b.return_(b.staticCall(6605, CpsDefaultGroovyMethods.class, "max", b.localVariable("self"), b.new_(6605, ClosureComparator.class, b.localVariable("closure")))))), b.declareVariable(6607, int.class, "first", b.constant(true)), b.declareVariable(6608, Object.class, "answer", b.constant(null)), b.declareVariable(6609, Object.class, "answerValue", b.constant(null)), b.forInLoop(6610, null, Object.class, "item", b.localVariable("self"), b.block(b.declareVariable(6611, Object.class, "value", b.functionCall(6611, b.localVariable("closure"), "call", b.localVariable("item"))), b.if_(b.localVariable("first"), b.block(b.assign(6613, b.localVariable("first"), b.constant(false)), b.assign(6614, b.localVariable("answer"), b.localVariable("item")), b.assign(6615, b.localVariable("answerValue"), b.localVariable("value"))), b.if_(b.functionCall(6616, b.constant(ScriptBytecodeAdapter.class), "compareLessThan", b.localVariable("answerValue"), b.localVariable("value")), b.block(b.assign(6617, b.localVariable("answer"), b.localVariable("item")), b.assign(6618, b.localVariable("answerValue"), b.localVariable("value"))))))), b.return_(b.localVariable("answer"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Object max(Iterator<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "max", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "max", self, closure))) {
            return DefaultGroovyMethods.max(self, closure);
        }
        Builder b = new Builder(loc("max"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(6645, CpsDefaultGroovyMethods.class, "max", b.cast(6645, b.staticCall(6645, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), Iterable.class, false), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Object max(Object[] self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "max", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "max", self, closure))) {
            return DefaultGroovyMethods.max(self, closure);
        }
        Builder b = new Builder(loc("max"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(6668, CpsDefaultGroovyMethods.class, "max", b.cast(6668, b.staticCall(6668, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), Iterable.class, false), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<K, V >Map<Object, Object> withDefault(Map<Object, Object> self, Closure init) {
        if ((!Caller.isAsynchronous(self, "withDefault", init))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "withDefault", self, init))) {
            return DefaultGroovyMethods.withDefault(self, init);
        }
        Builder b = new Builder(loc("withDefault"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "init"), b.block(b.return_(b.functionCall(7750, b.constant(MapWithDefault.class), "newInstance", b.localVariable("self"), b.localVariable("init")))));
        throw new CpsCallableInvocation(f, null, self, init);
    }

    public static<T >List<Object> withDefault(List<Object> self, Closure init) {
        if ((!Caller.isAsynchronous(self, "withDefault", init))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "withDefault", self, init))) {
            return DefaultGroovyMethods.withDefault(self, init);
        }
        Builder b = new Builder(loc("withDefault"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "init"), b.block(b.return_(b.staticCall(7765, CpsDefaultGroovyMethods.class, "withLazyDefault", b.localVariable("self"), b.localVariable("init")))));
        throw new CpsCallableInvocation(f, null, self, init);
    }

    public static<T >List<Object> withLazyDefault(List<Object> self, Closure init) {
        if ((!Caller.isAsynchronous(self, "withLazyDefault", init))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "withLazyDefault", self, init))) {
            return DefaultGroovyMethods.withLazyDefault(self, init);
        }
        Builder b = new Builder(loc("withLazyDefault"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "init"), b.block(b.return_(b.functionCall(7811, b.constant(ListWithDefault.class), "newInstance", b.localVariable("self"), b.constant(true), b.localVariable("init")))));
        throw new CpsCallableInvocation(f, null, self, init);
    }

    public static<T >List<Object> withEagerDefault(List<Object> self, Closure init) {
        if ((!Caller.isAsynchronous(self, "withEagerDefault", init))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "withEagerDefault", self, init))) {
            return DefaultGroovyMethods.withEagerDefault(self, init);
        }
        Builder b = new Builder(loc("withEagerDefault"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "init"), b.block(b.return_(b.functionCall(7851, b.constant(ListWithDefault.class), "newInstance", b.localVariable("self"), b.constant(false), b.localVariable("init")))));
        throw new CpsCallableInvocation(f, null, self, init);
    }

    public static<K, V >Map<Object, Object> sort(Map<Object, Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "sort", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "sort", self, closure))) {
            return DefaultGroovyMethods.sort(self, closure);
        }
        Builder b = new Builder(loc("sort"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(8129, Map.class, "result", b.new_(8129, LinkedHashMap.class)), b.declareVariable(8130, List.class, "entries", b.staticCall(8130, CpsDefaultGroovyMethods.class, "asList", b.cast(8130, b.functionCall(8130, b.localVariable("self"), "entrySet"), Iterable.class, false))), b.staticCall(8131, CpsDefaultGroovyMethods.class, "sort", b.cast(8131, b.localVariable("entries"), Iterable.class, false), b.localVariable("closure")), b.forInLoop(8132, null, java.util.Map.Entry.class, "entry", b.localVariable("entries"), b.block(b.functionCall(8133, b.localVariable("result"), "put", b.functionCall(8133, b.localVariable("entry"), "getKey"), b.functionCall(8133, b.localVariable("entry"), "getValue")))), b.return_(b.localVariable("result"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Iterator<Object> sort(Iterator<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "sort", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "sort", self, closure))) {
            return DefaultGroovyMethods.sort(self, closure);
        }
        Builder b = new Builder(loc("sort"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.functionCall(8338, b.staticCall(8338, CpsDefaultGroovyMethods.class, "sort", b.cast(8338, b.staticCall(8338, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), Iterable.class, false), b.localVariable("closure")), "listIterator"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Object[] sort(Object[] self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "sort", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "sort", self, closure))) {
            return DefaultGroovyMethods.sort(self, closure);
        }
        Builder b = new Builder(loc("sort"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(8358, CpsDefaultGroovyMethods.class, "sort", b.localVariable("self"), b.constant(false), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Object[] sort(Object[] self, boolean mutate, Closure closure) {
        if ((!Caller.isAsynchronous(self, "sort", mutate, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "sort", self, mutate, closure))) {
            return DefaultGroovyMethods.sort(self, mutate, closure);
        }
        Builder b = new Builder(loc("sort"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "mutate", "closure"), b.block(b.declareVariable(8388, Object[].class, "answer", b.cast(8388, b.functionCall(8388, b.staticCall(8388, CpsDefaultGroovyMethods.class, "sort", b.cast(8388, b.staticCall(8388, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")), Iterable.class, false), b.localVariable("closure")), "toArray"), Object[].class, false)), b.if_(b.localVariable("mutate"), b.block(b.functionCall(8390, b.constant(System.class), "arraycopy", b.localVariable("answer"), b.constant(0), b.localVariable("self"), b.constant(0), b.property(8390, b.localVariable("answer"), "length")))), b.return_(b.ternaryOp(b.localVariable("mutate"), b.localVariable("self"), b.localVariable("answer")))));
        throw new CpsCallableInvocation(f, null, self, mutate, closure);
    }

    public static<T >List<Object> sort(Iterable<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "sort", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "sort", self, closure))) {
            return DefaultGroovyMethods.sort(self, closure);
        }
        Builder b = new Builder(loc("sort"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(8438, CpsDefaultGroovyMethods.class, "sort", b.localVariable("self"), b.constant(true), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >List<Object> sort(Iterable<Object> self, boolean mutate, Closure closure) {
        if ((!Caller.isAsynchronous(self, "sort", mutate, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "sort", self, mutate, closure))) {
            return DefaultGroovyMethods.sort(self, mutate, closure);
        }
        Builder b = new Builder(loc("sort"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "mutate", "closure"), b.block(b.declareVariable(8470, List.class, "list", b.ternaryOp(b.localVariable("mutate"), b.staticCall(8470, CpsDefaultGroovyMethods.class, "asList", b.localVariable("self")), b.staticCall(8470, CpsDefaultGroovyMethods.class, "toList", b.localVariable("self")))), b.declareVariable(8472, int.class, "params", b.functionCall(8472, b.localVariable("closure"), "getMaximumNumberOfParameters")), b.if_(b.compareEqual(8473, b.localVariable("params"), b.constant(1)), b.block(b.functionCall(8474, b.constant(Collections.class), "sort", b.localVariable("list"), b.new_(8474, OrderBy.class, b.localVariable("closure")))), b.block(b.functionCall(8476, b.constant(Collections.class), "sort", b.localVariable("list"), b.new_(8476, ClosureComparator.class, b.localVariable("closure"))))), b.return_(b.localVariable("list"))));
        throw new CpsCallableInvocation(f, null, self, mutate, closure);
    }

    public static<T >List<Object> toSorted(Iterable<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "toSorted", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "toSorted", self, closure))) {
            return DefaultGroovyMethods.toSorted(self, closure);
        }
        Builder b = new Builder(loc("toSorted"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(8568, Comparator.class, "comparator", b.ternaryOp(b.compareEqual(8568, b.functionCall(8568, b.localVariable("closure"), "getMaximumNumberOfParameters"), b.constant(1)), b.new_(8568, OrderBy.class, b.localVariable("closure")), b.new_(8568, ClosureComparator.class, b.localVariable("closure")))), b.return_(b.staticCall(8569, CpsDefaultGroovyMethods.class, "toSorted", b.localVariable("self"), b.localVariable("comparator")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Iterator<Object> toSorted(Iterator<Object> self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "toSorted", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "toSorted", self, closure))) {
            return DefaultGroovyMethods.toSorted(self, closure);
        }
        Builder b = new Builder(loc("toSorted"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(8620, Comparator.class, "comparator", b.ternaryOp(b.compareEqual(8620, b.functionCall(8620, b.localVariable("closure"), "getMaximumNumberOfParameters"), b.constant(1)), b.new_(8620, OrderBy.class, b.localVariable("closure")), b.new_(8620, ClosureComparator.class, b.localVariable("closure")))), b.return_(b.staticCall(8621, CpsDefaultGroovyMethods.class, "toSorted", b.localVariable("self"), b.localVariable("comparator")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static<T >Object[] toSorted(Object[] self, Closure condition) {
        if ((!Caller.isAsynchronous(self, "toSorted", condition))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "toSorted", self, condition))) {
            return DefaultGroovyMethods.toSorted(self, condition);
        }
        Builder b = new Builder(loc("toSorted"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "condition"), b.block(b.declareVariable(8674, Comparator.class, "comparator", b.ternaryOp(b.compareEqual(8674, b.functionCall(8674, b.localVariable("condition"), "getMaximumNumberOfParameters"), b.constant(1)), b.new_(8674, OrderBy.class, b.localVariable("condition")), b.new_(8674, ClosureComparator.class, b.localVariable("condition")))), b.return_(b.staticCall(8675, CpsDefaultGroovyMethods.class, "toSorted", b.localVariable("self"), b.localVariable("comparator")))));
        throw new CpsCallableInvocation(f, null, self, condition);
    }

    public static<K, V >Map<Object, Object> toSorted(Map<Object, Object> self, Closure condition) {
        if ((!Caller.isAsynchronous(self, "toSorted", condition))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "toSorted", self, condition))) {
            return DefaultGroovyMethods.toSorted(self, condition);
        }
        Builder b = new Builder(loc("toSorted"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "condition"), b.block(b.declareVariable(8751, Comparator.class, "comparator", b.ternaryOp(b.compareEqual(8751, b.functionCall(8751, b.localVariable("condition"), "getMaximumNumberOfParameters"), b.constant(1)), b.new_(8751, OrderBy.class, b.localVariable("condition")), b.new_(8751, ClosureComparator.class, b.localVariable("condition")))), b.return_(b.staticCall(8752, CpsDefaultGroovyMethods.class, "toSorted", b.localVariable("self"), b.localVariable("comparator")))));
        throw new CpsCallableInvocation(f, null, self, condition);
    }

    public static<T >Collection<Object> flatten(Iterable<Object> self, Closure<?> flattenUsing) {
        if ((!Caller.isAsynchronous(self, "flatten", flattenUsing))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "flatten", self, flattenUsing))) {
            return DefaultGroovyMethods.flatten(self, flattenUsing);
        }
        Builder b = new Builder(loc("flatten"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "flattenUsing"), b.block(b.return_(b.staticCall(12163, CpsDefaultGroovyMethods.class, "flatten", b.localVariable("self"), b.functionCall(12163, b.constant(DefaultGroovyMethodsSupport.class), "createSimilarCollection", b.localVariable("self")), b.localVariable("flattenUsing")))));
        throw new CpsCallableInvocation(f, null, self, flattenUsing);
    }

    public static void times(Number self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "times", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "times", self, closure))) {
            DefaultGroovyMethods.times(self, closure);
            return ;
        }
        Builder b = new Builder(loc("times"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.forLoop(null, b.sequence(b.declareVariable(14342, int.class, "i", b.constant(0)), b.declareVariable(14342, int.class, "size", b.functionCall(14342, b.localVariable("self"), "intValue"))), b.lessThan(14342, b.localVariable("i"), b.localVariable("size")), b.sequence(b.postfixInc(14342, b.localVariable("i"))), b.block(b.functionCall(14343, b.localVariable("closure"), "call", b.localVariable("i")), b.if_(b.compareEqual(14344, b.functionCall(14344, b.localVariable("closure"), "getDirective"), b.property(14344, b.constant(Closure.class), "DONE")), b.block(b.break_(null)))))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static void upto(Number self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "upto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "upto", self, to, closure))) {
            DefaultGroovyMethods.upto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("upto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14360, int.class, "self1", b.functionCall(14360, b.localVariable("self"), "intValue")), b.declareVariable(14361, int.class, "to1", b.functionCall(14361, b.localVariable("to"), "intValue")), b.if_(b.lessThanEqual(14362, b.localVariable("self1"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14363, int.class, "i", b.localVariable("self1"))), b.lessThanEqual(14363, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixInc(14363, b.localVariable("i"))), b.block(b.functionCall(14364, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14367, b.new_(14367, GroovyRuntimeException.class, b.plus(14367, b.plus(14367, b.plus(14367, b.plus(14367, b.constant("The argument ("), b.localVariable("to")), b.constant(") to upto() cannot be less than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void upto(long self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "upto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "upto", self, to, closure))) {
            DefaultGroovyMethods.upto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("upto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14381, long.class, "to1", b.functionCall(14381, b.localVariable("to"), "longValue")), b.if_(b.lessThanEqual(14382, b.localVariable("self"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14383, long.class, "i", b.localVariable("self"))), b.lessThanEqual(14383, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixInc(14383, b.localVariable("i"))), b.block(b.functionCall(14384, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14387, b.new_(14387, GroovyRuntimeException.class, b.plus(14387, b.plus(14387, b.plus(14387, b.plus(14387, b.constant("The argument ("), b.localVariable("to")), b.constant(") to upto() cannot be less than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void upto(Long self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "upto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "upto", self, to, closure))) {
            DefaultGroovyMethods.upto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("upto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14401, long.class, "to1", b.functionCall(14401, b.localVariable("to"), "longValue")), b.if_(b.lessThanEqual(14402, b.localVariable("self"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14403, long.class, "i", b.localVariable("self"))), b.lessThanEqual(14403, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixInc(14403, b.localVariable("i"))), b.block(b.functionCall(14404, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14407, b.new_(14407, GroovyRuntimeException.class, b.plus(14407, b.plus(14407, b.plus(14407, b.plus(14407, b.constant("The argument ("), b.localVariable("to")), b.constant(") to upto() cannot be less than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void upto(float self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "upto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "upto", self, to, closure))) {
            DefaultGroovyMethods.upto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("upto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14421, float.class, "to1", b.functionCall(14421, b.localVariable("to"), "floatValue")), b.if_(b.lessThanEqual(14422, b.localVariable("self"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14423, float.class, "i", b.localVariable("self"))), b.lessThanEqual(14423, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixInc(14423, b.localVariable("i"))), b.block(b.functionCall(14424, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14427, b.new_(14427, GroovyRuntimeException.class, b.plus(14427, b.plus(14427, b.plus(14427, b.plus(14427, b.constant("The argument ("), b.localVariable("to")), b.constant(") to upto() cannot be less than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void upto(Float self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "upto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "upto", self, to, closure))) {
            DefaultGroovyMethods.upto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("upto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14441, float.class, "to1", b.functionCall(14441, b.localVariable("to"), "floatValue")), b.if_(b.lessThanEqual(14442, b.localVariable("self"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14443, float.class, "i", b.localVariable("self"))), b.lessThanEqual(14443, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixInc(14443, b.localVariable("i"))), b.block(b.functionCall(14444, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14447, b.new_(14447, GroovyRuntimeException.class, b.plus(14447, b.plus(14447, b.plus(14447, b.plus(14447, b.constant("The argument ("), b.localVariable("to")), b.constant(") to upto() cannot be less than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void upto(double self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "upto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "upto", self, to, closure))) {
            DefaultGroovyMethods.upto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("upto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14461, double.class, "to1", b.functionCall(14461, b.localVariable("to"), "doubleValue")), b.if_(b.lessThanEqual(14462, b.localVariable("self"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14463, double.class, "i", b.localVariable("self"))), b.lessThanEqual(14463, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixInc(14463, b.localVariable("i"))), b.block(b.functionCall(14464, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14467, b.new_(14467, GroovyRuntimeException.class, b.plus(14467, b.plus(14467, b.plus(14467, b.plus(14467, b.constant("The argument ("), b.localVariable("to")), b.constant(") to upto() cannot be less than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void upto(Double self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "upto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "upto", self, to, closure))) {
            DefaultGroovyMethods.upto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("upto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14481, double.class, "to1", b.functionCall(14481, b.localVariable("to"), "doubleValue")), b.if_(b.lessThanEqual(14482, b.localVariable("self"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14483, double.class, "i", b.localVariable("self"))), b.lessThanEqual(14483, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixInc(14483, b.localVariable("i"))), b.block(b.functionCall(14484, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14487, b.new_(14487, GroovyRuntimeException.class, b.plus(14487, b.plus(14487, b.plus(14487, b.plus(14487, b.constant("The argument ("), b.localVariable("to")), b.constant(") to upto() cannot be less than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void upto(BigInteger self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "upto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "upto", self, to, closure))) {
            DefaultGroovyMethods.upto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("upto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.if_(b.instanceOf(14505, b.localVariable("to"), b.constant(BigDecimal.class)), b.block(b.declareVariable(14506, BigDecimal.class, "one", b.functionCall(14506, b.constant(BigDecimal.class), "valueOf", b.constant(10), b.constant(1))), b.declareVariable(14507, BigDecimal.class, "self1", b.new_(14507, BigDecimal.class, b.localVariable("self"))), b.declareVariable(14508, BigDecimal.class, "to1", b.cast(14508, b.localVariable("to"), BigDecimal.class, false)), b.if_(b.lessThanEqual(14509, b.functionCall(14509, b.localVariable("self1"), "compareTo", b.localVariable("to1")), b.constant(0)), b.block(b.forLoop(null, b.sequence(b.declareVariable(14510, BigDecimal.class, "i", b.localVariable("self1"))), b.lessThanEqual(14510, b.functionCall(14510, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14510, b.localVariable("i"), b.functionCall(14510, b.localVariable("i"), "add", b.localVariable("one")))), b.block(b.functionCall(14511, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14514, b.new_(14514, GroovyRuntimeException.class, b.functionCall(14515, b.constant(MessageFormat.class), "format", b.constant("The argument ({0}) to upto() cannot be less than the value ({1}) it''s called on."), b.localVariable("to"), b.localVariable("self")))))), b.if_(b.instanceOf(14518, b.localVariable("to"), b.constant(BigInteger.class)), b.block(b.declareVariable(14519, BigInteger.class, "one", b.functionCall(14519, b.constant(BigInteger.class), "valueOf", b.constant(1))), b.declareVariable(14520, BigInteger.class, "to1", b.cast(14520, b.localVariable("to"), BigInteger.class, false)), b.if_(b.lessThanEqual(14521, b.functionCall(14521, b.localVariable("self"), "compareTo", b.localVariable("to1")), b.constant(0)), b.block(b.forLoop(null, b.sequence(b.declareVariable(14522, BigInteger.class, "i", b.localVariable("self"))), b.lessThanEqual(14522, b.functionCall(14522, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14522, b.localVariable("i"), b.functionCall(14522, b.localVariable("i"), "add", b.localVariable("one")))), b.block(b.functionCall(14523, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14526, b.new_(14526, GroovyRuntimeException.class, b.functionCall(14527, b.constant(MessageFormat.class), "format", b.constant("The argument ({0}) to upto() cannot be less than the value ({1}) it''s called on."), b.localVariable("to"), b.localVariable("self")))))), b.block(b.declareVariable(14530, BigInteger.class, "one", b.functionCall(14530, b.constant(BigInteger.class), "valueOf", b.constant(1))), b.declareVariable(14531, BigInteger.class, "to1", b.new_(14531, BigInteger.class, b.functionCall(14531, b.localVariable("to"), "toString"))), b.if_(b.lessThanEqual(14532, b.functionCall(14532, b.localVariable("self"), "compareTo", b.localVariable("to1")), b.constant(0)), b.block(b.forLoop(null, b.sequence(b.declareVariable(14533, BigInteger.class, "i", b.localVariable("self"))), b.lessThanEqual(14533, b.functionCall(14533, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14533, b.localVariable("i"), b.functionCall(14533, b.localVariable("i"), "add", b.localVariable("one")))), b.block(b.functionCall(14534, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14537, b.new_(14537, GroovyRuntimeException.class, b.functionCall(14537, b.constant(MessageFormat.class), "format", b.constant("The argument ({0}) to upto() cannot be less than the value ({1}) it''s called on."), b.localVariable("to"), b.localVariable("self"))))))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void upto(BigDecimal self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "upto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "upto", self, to, closure))) {
            DefaultGroovyMethods.upto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("upto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14557, BigDecimal.class, "one", b.functionCall(14557, b.constant(BigDecimal.class), "valueOf", b.constant(10), b.constant(1))), b.if_(b.instanceOf(14558, b.localVariable("to"), b.constant(BigDecimal.class)), b.block(b.declareVariable(14559, BigDecimal.class, "to1", b.cast(14559, b.localVariable("to"), BigDecimal.class, false)), b.if_(b.lessThanEqual(14560, b.functionCall(14560, b.localVariable("self"), "compareTo", b.localVariable("to1")), b.constant(0)), b.block(b.forLoop(null, b.sequence(b.declareVariable(14561, BigDecimal.class, "i", b.localVariable("self"))), b.lessThanEqual(14561, b.functionCall(14561, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14561, b.localVariable("i"), b.functionCall(14561, b.localVariable("i"), "add", b.localVariable("one")))), b.block(b.functionCall(14562, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14565, b.new_(14565, GroovyRuntimeException.class, b.plus(14565, b.plus(14565, b.plus(14565, b.plus(14565, b.constant("The argument ("), b.localVariable("to")), b.constant(") to upto() cannot be less than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))), b.if_(b.instanceOf(14567, b.localVariable("to"), b.constant(BigInteger.class)), b.block(b.declareVariable(14568, BigDecimal.class, "to1", b.new_(14568, BigDecimal.class, b.cast(14568, b.localVariable("to"), BigInteger.class, false))), b.if_(b.lessThanEqual(14569, b.functionCall(14569, b.localVariable("self"), "compareTo", b.localVariable("to1")), b.constant(0)), b.block(b.forLoop(null, b.sequence(b.declareVariable(14570, BigDecimal.class, "i", b.localVariable("self"))), b.lessThanEqual(14570, b.functionCall(14570, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14570, b.localVariable("i"), b.functionCall(14570, b.localVariable("i"), "add", b.localVariable("one")))), b.block(b.functionCall(14571, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14574, b.new_(14574, GroovyRuntimeException.class, b.plus(14574, b.plus(14574, b.plus(14574, b.plus(14574, b.constant("The argument ("), b.localVariable("to")), b.constant(") to upto() cannot be less than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))), b.block(b.declareVariable(14577, BigDecimal.class, "to1", b.new_(14577, BigDecimal.class, b.functionCall(14577, b.localVariable("to"), "toString"))), b.if_(b.lessThanEqual(14578, b.functionCall(14578, b.localVariable("self"), "compareTo", b.localVariable("to1")), b.constant(0)), b.block(b.forLoop(null, b.sequence(b.declareVariable(14579, BigDecimal.class, "i", b.localVariable("self"))), b.lessThanEqual(14579, b.functionCall(14579, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14579, b.localVariable("i"), b.functionCall(14579, b.localVariable("i"), "add", b.localVariable("one")))), b.block(b.functionCall(14580, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14583, b.new_(14583, GroovyRuntimeException.class, b.plus(14583, b.plus(14583, b.plus(14583, b.plus(14583, b.constant("The argument ("), b.localVariable("to")), b.constant(") to upto() cannot be less than the value (")), b.localVariable("self")), b.constant(") it's called on."))))))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void downto(Number self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "downto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "downto", self, to, closure))) {
            DefaultGroovyMethods.downto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("downto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14598, int.class, "self1", b.functionCall(14598, b.localVariable("self"), "intValue")), b.declareVariable(14599, int.class, "to1", b.functionCall(14599, b.localVariable("to"), "intValue")), b.if_(b.greaterThanEqual(14600, b.localVariable("self1"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14601, int.class, "i", b.localVariable("self1"))), b.greaterThanEqual(14601, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixDec(14601, b.localVariable("i"))), b.block(b.functionCall(14602, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14605, b.new_(14605, GroovyRuntimeException.class, b.plus(14605, b.plus(14605, b.plus(14605, b.plus(14605, b.constant("The argument ("), b.localVariable("to")), b.constant(") to downto() cannot be greater than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void downto(long self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "downto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "downto", self, to, closure))) {
            DefaultGroovyMethods.downto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("downto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14619, long.class, "to1", b.functionCall(14619, b.localVariable("to"), "longValue")), b.if_(b.greaterThanEqual(14620, b.localVariable("self"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14621, long.class, "i", b.localVariable("self"))), b.greaterThanEqual(14621, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixDec(14621, b.localVariable("i"))), b.block(b.functionCall(14622, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14625, b.new_(14625, GroovyRuntimeException.class, b.plus(14625, b.plus(14625, b.plus(14625, b.plus(14625, b.constant("The argument ("), b.localVariable("to")), b.constant(") to downto() cannot be greater than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void downto(Long self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "downto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "downto", self, to, closure))) {
            DefaultGroovyMethods.downto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("downto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14639, long.class, "to1", b.functionCall(14639, b.localVariable("to"), "longValue")), b.if_(b.greaterThanEqual(14640, b.localVariable("self"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14641, long.class, "i", b.localVariable("self"))), b.greaterThanEqual(14641, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixDec(14641, b.localVariable("i"))), b.block(b.functionCall(14642, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14645, b.new_(14645, GroovyRuntimeException.class, b.plus(14645, b.plus(14645, b.plus(14645, b.plus(14645, b.constant("The argument ("), b.localVariable("to")), b.constant(") to downto() cannot be greater than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void downto(float self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "downto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "downto", self, to, closure))) {
            DefaultGroovyMethods.downto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("downto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14659, float.class, "to1", b.functionCall(14659, b.localVariable("to"), "floatValue")), b.if_(b.greaterThanEqual(14660, b.localVariable("self"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14661, float.class, "i", b.localVariable("self"))), b.greaterThanEqual(14661, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixDec(14661, b.localVariable("i"))), b.block(b.functionCall(14662, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14665, b.new_(14665, GroovyRuntimeException.class, b.plus(14665, b.plus(14665, b.plus(14665, b.plus(14665, b.constant("The argument ("), b.localVariable("to")), b.constant(") to downto() cannot be greater than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void downto(Float self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "downto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "downto", self, to, closure))) {
            DefaultGroovyMethods.downto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("downto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14678, float.class, "to1", b.functionCall(14678, b.localVariable("to"), "floatValue")), b.if_(b.greaterThanEqual(14679, b.localVariable("self"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14680, float.class, "i", b.localVariable("self"))), b.greaterThanEqual(14680, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixDec(14680, b.localVariable("i"))), b.block(b.functionCall(14681, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14684, b.new_(14684, GroovyRuntimeException.class, b.plus(14684, b.plus(14684, b.plus(14684, b.plus(14684, b.constant("The argument ("), b.localVariable("to")), b.constant(") to downto() cannot be greater than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void downto(double self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "downto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "downto", self, to, closure))) {
            DefaultGroovyMethods.downto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("downto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14697, double.class, "to1", b.functionCall(14697, b.localVariable("to"), "doubleValue")), b.if_(b.greaterThanEqual(14698, b.localVariable("self"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14699, double.class, "i", b.localVariable("self"))), b.greaterThanEqual(14699, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixDec(14699, b.localVariable("i"))), b.block(b.functionCall(14700, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14703, b.new_(14703, GroovyRuntimeException.class, b.plus(14703, b.plus(14703, b.plus(14703, b.plus(14703, b.constant("The argument ("), b.localVariable("to")), b.constant(") to downto() cannot be greater than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void downto(Double self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "downto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "downto", self, to, closure))) {
            DefaultGroovyMethods.downto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("downto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14716, double.class, "to1", b.functionCall(14716, b.localVariable("to"), "doubleValue")), b.if_(b.greaterThanEqual(14717, b.localVariable("self"), b.localVariable("to1")), b.block(b.forLoop(null, b.sequence(b.declareVariable(14718, double.class, "i", b.localVariable("self"))), b.greaterThanEqual(14718, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.postfixDec(14718, b.localVariable("i"))), b.block(b.functionCall(14719, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14722, b.new_(14722, GroovyRuntimeException.class, b.plus(14722, b.plus(14722, b.plus(14722, b.plus(14722, b.constant("The argument ("), b.localVariable("to")), b.constant(") to downto() cannot be greater than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void downto(BigInteger self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "downto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "downto", self, to, closure))) {
            DefaultGroovyMethods.downto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("downto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.if_(b.instanceOf(14735, b.localVariable("to"), b.constant(BigDecimal.class)), b.block(b.declareVariable(14736, BigDecimal.class, "one", b.functionCall(14736, b.constant(BigDecimal.class), "valueOf", b.constant(10), b.constant(1))), b.declareVariable(14737, BigDecimal.class, "to1", b.cast(14737, b.localVariable("to"), BigDecimal.class, false)), b.declareVariable(14738, BigDecimal.class, "selfD", b.new_(14738, BigDecimal.class, b.localVariable("self"))), b.if_(b.greaterThanEqual(14739, b.functionCall(14739, b.localVariable("selfD"), "compareTo", b.localVariable("to1")), b.constant(0)), b.block(b.forLoop(null, b.sequence(b.declareVariable(14740, BigDecimal.class, "i", b.localVariable("selfD"))), b.greaterThanEqual(14740, b.functionCall(14740, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14740, b.localVariable("i"), b.functionCall(14740, b.localVariable("i"), "subtract", b.localVariable("one")))), b.block(b.functionCall(14741, b.localVariable("closure"), "call", b.functionCall(14741, b.localVariable("i"), "toBigInteger"))))), b.throw_(14744, b.new_(14744, GroovyRuntimeException.class, b.functionCall(14745, b.constant(MessageFormat.class), "format", b.constant("The argument ({0}) to downto() cannot be greater than the value ({1}) it''s called on."), b.localVariable("to"), b.localVariable("self")))))), b.if_(b.instanceOf(14748, b.localVariable("to"), b.constant(BigInteger.class)), b.block(b.declareVariable(14749, BigInteger.class, "one", b.functionCall(14749, b.constant(BigInteger.class), "valueOf", b.constant(1))), b.declareVariable(14750, BigInteger.class, "to1", b.cast(14750, b.localVariable("to"), BigInteger.class, false)), b.if_(b.greaterThanEqual(14751, b.functionCall(14751, b.localVariable("self"), "compareTo", b.localVariable("to1")), b.constant(0)), b.block(b.forLoop(null, b.sequence(b.declareVariable(14752, BigInteger.class, "i", b.localVariable("self"))), b.greaterThanEqual(14752, b.functionCall(14752, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14752, b.localVariable("i"), b.functionCall(14752, b.localVariable("i"), "subtract", b.localVariable("one")))), b.block(b.functionCall(14753, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14756, b.new_(14756, GroovyRuntimeException.class, b.functionCall(14757, b.constant(MessageFormat.class), "format", b.constant("The argument ({0}) to downto() cannot be greater than the value ({1}) it''s called on."), b.localVariable("to"), b.localVariable("self")))))), b.block(b.declareVariable(14761, BigInteger.class, "one", b.functionCall(14761, b.constant(BigInteger.class), "valueOf", b.constant(1))), b.declareVariable(14762, BigInteger.class, "to1", b.new_(14762, BigInteger.class, b.functionCall(14762, b.localVariable("to"), "toString"))), b.if_(b.greaterThanEqual(14763, b.functionCall(14763, b.localVariable("self"), "compareTo", b.localVariable("to1")), b.constant(0)), b.block(b.forLoop(null, b.sequence(b.declareVariable(14764, BigInteger.class, "i", b.localVariable("self"))), b.greaterThanEqual(14764, b.functionCall(14764, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14764, b.localVariable("i"), b.functionCall(14764, b.localVariable("i"), "subtract", b.localVariable("one")))), b.block(b.functionCall(14765, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14768, b.new_(14768, GroovyRuntimeException.class, b.functionCall(14769, b.constant(MessageFormat.class), "format", b.constant("The argument ({0}) to downto() cannot be greater than the value ({1}) it''s called on."), b.localVariable("to"), b.localVariable("self"))))))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void downto(BigDecimal self, Number to, Closure closure) {
        if ((!Caller.isAsynchronous(self, "downto", to, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "downto", self, to, closure))) {
            DefaultGroovyMethods.downto(self, to, closure);
            return ;
        }
        Builder b = new Builder(loc("downto"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "closure"), b.block(b.declareVariable(14789, BigDecimal.class, "one", b.functionCall(14789, b.constant(BigDecimal.class), "valueOf", b.constant(10), b.constant(1))), b.if_(b.instanceOf(14790, b.localVariable("to"), b.constant(BigDecimal.class)), b.block(b.declareVariable(14791, BigDecimal.class, "to1", b.cast(14791, b.localVariable("to"), BigDecimal.class, false)), b.if_(b.greaterThanEqual(14792, b.functionCall(14792, b.localVariable("self"), "compareTo", b.localVariable("to1")), b.constant(0)), b.block(b.forLoop(null, b.sequence(b.declareVariable(14793, BigDecimal.class, "i", b.localVariable("self"))), b.greaterThanEqual(14793, b.functionCall(14793, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14793, b.localVariable("i"), b.functionCall(14793, b.localVariable("i"), "subtract", b.localVariable("one")))), b.block(b.functionCall(14794, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14797, b.new_(14797, GroovyRuntimeException.class, b.plus(14797, b.plus(14797, b.plus(14797, b.plus(14797, b.constant("The argument ("), b.localVariable("to")), b.constant(") to downto() cannot be greater than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))), b.if_(b.instanceOf(14798, b.localVariable("to"), b.constant(BigInteger.class)), b.block(b.declareVariable(14799, BigDecimal.class, "to1", b.new_(14799, BigDecimal.class, b.cast(14799, b.localVariable("to"), BigInteger.class, false))), b.if_(b.greaterThanEqual(14800, b.functionCall(14800, b.localVariable("self"), "compareTo", b.localVariable("to1")), b.constant(0)), b.block(b.forLoop(null, b.sequence(b.declareVariable(14801, BigDecimal.class, "i", b.localVariable("self"))), b.greaterThanEqual(14801, b.functionCall(14801, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14801, b.localVariable("i"), b.functionCall(14801, b.localVariable("i"), "subtract", b.localVariable("one")))), b.block(b.functionCall(14802, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14805, b.new_(14805, GroovyRuntimeException.class, b.plus(14805, b.plus(14805, b.plus(14805, b.plus(14805, b.constant("The argument ("), b.localVariable("to")), b.constant(") to downto() cannot be greater than the value (")), b.localVariable("self")), b.constant(") it's called on.")))))), b.block(b.declareVariable(14807, BigDecimal.class, "to1", b.new_(14807, BigDecimal.class, b.functionCall(14807, b.localVariable("to"), "toString"))), b.if_(b.greaterThanEqual(14808, b.functionCall(14808, b.localVariable("self"), "compareTo", b.localVariable("to1")), b.constant(0)), b.block(b.forLoop(null, b.sequence(b.declareVariable(14809, BigDecimal.class, "i", b.localVariable("self"))), b.greaterThanEqual(14809, b.functionCall(14809, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14809, b.localVariable("i"), b.functionCall(14809, b.localVariable("i"), "subtract", b.localVariable("one")))), b.block(b.functionCall(14810, b.localVariable("closure"), "call", b.localVariable("i"))))), b.throw_(14813, b.new_(14813, GroovyRuntimeException.class, b.plus(14813, b.plus(14813, b.plus(14813, b.plus(14813, b.constant("The argument ("), b.localVariable("to")), b.constant(") to downto() cannot be greater than the value (")), b.localVariable("self")), b.constant(") it's called on."))))))))));
        throw new CpsCallableInvocation(f, null, self, to, closure);
    }

    public static void step(Number self, Number to, Number stepNumber, Closure closure) {
        if ((!Caller.isAsynchronous(self, "step", to, stepNumber, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "step", self, to, stepNumber, closure))) {
            DefaultGroovyMethods.step(self, to, stepNumber, closure);
            return ;
        }
        Builder b = new Builder(loc("step"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "to", "stepNumber", "closure"), b.block(b.if_(b.logicalOr(14832, b.logicalOr(14832, b.instanceOf(14832, b.localVariable("self"), b.constant(BigDecimal.class)), b.instanceOf(14832, b.localVariable("to"), b.constant(BigDecimal.class))), b.instanceOf(14832, b.localVariable("stepNumber"), b.constant(BigDecimal.class))), b.block(b.declareVariable(14833, BigDecimal.class, "zero", b.functionCall(14833, b.constant(BigDecimal.class), "valueOf", b.constant(0), b.constant(1))), b.declareVariable(14834, BigDecimal.class, "self1", b.ternaryOp(b.instanceOf(14834, b.localVariable("self"), b.constant(BigDecimal.class)), b.cast(14834, b.localVariable("self"), BigDecimal.class, false), b.new_(14834, BigDecimal.class, b.functionCall(14834, b.localVariable("self"), "toString")))), b.declareVariable(14835, BigDecimal.class, "to1", b.ternaryOp(b.instanceOf(14835, b.localVariable("to"), b.constant(BigDecimal.class)), b.cast(14835, b.localVariable("to"), BigDecimal.class, false), b.new_(14835, BigDecimal.class, b.functionCall(14835, b.localVariable("to"), "toString")))), b.declareVariable(14836, BigDecimal.class, "stepNumber1", b.ternaryOp(b.instanceOf(14836, b.localVariable("stepNumber"), b.constant(BigDecimal.class)), b.cast(14836, b.localVariable("stepNumber"), BigDecimal.class, false), b.new_(14836, BigDecimal.class, b.functionCall(14836, b.localVariable("stepNumber"), "toString")))), b.if_(b.logicalAnd(14837, b.greaterThan(14837, b.functionCall(14837, b.localVariable("stepNumber1"), "compareTo", b.localVariable("zero")), b.constant(0)), b.greaterThan(14837, b.functionCall(14837, b.localVariable("to1"), "compareTo", b.localVariable("self1")), b.constant(0))), b.block(b.forLoop(null, b.sequence(b.declareVariable(14838, BigDecimal.class, "i", b.localVariable("self1"))), b.lessThan(14838, b.functionCall(14838, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14838, b.localVariable("i"), b.functionCall(14838, b.localVariable("i"), "add", b.localVariable("stepNumber1")))), b.block(b.functionCall(14839, b.localVariable("closure"), "call", b.localVariable("i"))))), b.if_(b.logicalAnd(14841, b.lessThan(14841, b.functionCall(14841, b.localVariable("stepNumber1"), "compareTo", b.localVariable("zero")), b.constant(0)), b.lessThan(14841, b.functionCall(14841, b.localVariable("to1"), "compareTo", b.localVariable("self1")), b.constant(0))), b.block(b.forLoop(null, b.sequence(b.declareVariable(14842, BigDecimal.class, "i", b.localVariable("self1"))), b.greaterThan(14842, b.functionCall(14842, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14842, b.localVariable("i"), b.functionCall(14842, b.localVariable("i"), "add", b.localVariable("stepNumber1")))), b.block(b.functionCall(14843, b.localVariable("closure"), "call", b.localVariable("i"))))), b.if_(b.compareNotEqual(14845, b.functionCall(14845, b.localVariable("self1"), "compareTo", b.localVariable("to1")), b.constant(0)), b.throw_(14846, b.new_(14846, GroovyRuntimeException.class, b.plus(14846, b.plus(14846, b.plus(14846, b.plus(14846, b.plus(14846, b.plus(14846, b.constant("Infinite loop in "), b.localVariable("self1")), b.constant(".step(")), b.localVariable("to1")), b.constant(", ")), b.localVariable("stepNumber1")), b.constant(")")))))))), b.if_(b.logicalOr(14847, b.logicalOr(14847, b.instanceOf(14847, b.localVariable("self"), b.constant(BigInteger.class)), b.instanceOf(14847, b.localVariable("to"), b.constant(BigInteger.class))), b.instanceOf(14847, b.localVariable("stepNumber"), b.constant(BigInteger.class))), b.block(b.declareVariable(14848, BigInteger.class, "zero", b.functionCall(14848, b.constant(BigInteger.class), "valueOf", b.constant(0))), b.declareVariable(14849, BigInteger.class, "self1", b.ternaryOp(b.instanceOf(14849, b.localVariable("self"), b.constant(BigInteger.class)), b.cast(14849, b.localVariable("self"), BigInteger.class, false), b.new_(14849, BigInteger.class, b.functionCall(14849, b.localVariable("self"), "toString")))), b.declareVariable(14850, BigInteger.class, "to1", b.ternaryOp(b.instanceOf(14850, b.localVariable("to"), b.constant(BigInteger.class)), b.cast(14850, b.localVariable("to"), BigInteger.class, false), b.new_(14850, BigInteger.class, b.functionCall(14850, b.localVariable("to"), "toString")))), b.declareVariable(14851, BigInteger.class, "stepNumber1", b.ternaryOp(b.instanceOf(14851, b.localVariable("stepNumber"), b.constant(BigInteger.class)), b.cast(14851, b.localVariable("stepNumber"), BigInteger.class, false), b.new_(14851, BigInteger.class, b.functionCall(14851, b.localVariable("stepNumber"), "toString")))), b.if_(b.logicalAnd(14852, b.greaterThan(14852, b.functionCall(14852, b.localVariable("stepNumber1"), "compareTo", b.localVariable("zero")), b.constant(0)), b.greaterThan(14852, b.functionCall(14852, b.localVariable("to1"), "compareTo", b.localVariable("self1")), b.constant(0))), b.block(b.forLoop(null, b.sequence(b.declareVariable(14853, BigInteger.class, "i", b.localVariable("self1"))), b.lessThan(14853, b.functionCall(14853, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14853, b.localVariable("i"), b.functionCall(14853, b.localVariable("i"), "add", b.localVariable("stepNumber1")))), b.block(b.functionCall(14854, b.localVariable("closure"), "call", b.localVariable("i"))))), b.if_(b.logicalAnd(14856, b.lessThan(14856, b.functionCall(14856, b.localVariable("stepNumber1"), "compareTo", b.localVariable("zero")), b.constant(0)), b.lessThan(14856, b.functionCall(14856, b.localVariable("to1"), "compareTo", b.localVariable("self1")), b.constant(0))), b.block(b.forLoop(null, b.sequence(b.declareVariable(14857, BigInteger.class, "i", b.localVariable("self1"))), b.greaterThan(14857, b.functionCall(14857, b.localVariable("i"), "compareTo", b.localVariable("to1")), b.constant(0)), b.sequence(b.assign(14857, b.localVariable("i"), b.functionCall(14857, b.localVariable("i"), "add", b.localVariable("stepNumber1")))), b.block(b.functionCall(14858, b.localVariable("closure"), "call", b.localVariable("i"))))), b.if_(b.compareNotEqual(14860, b.functionCall(14860, b.localVariable("self1"), "compareTo", b.localVariable("to1")), b.constant(0)), b.throw_(14861, b.new_(14861, GroovyRuntimeException.class, b.plus(14861, b.plus(14861, b.plus(14861, b.plus(14861, b.plus(14861, b.plus(14861, b.constant("Infinite loop in "), b.localVariable("self1")), b.constant(".step(")), b.localVariable("to1")), b.constant(", ")), b.localVariable("stepNumber1")), b.constant(")")))))))), b.block(b.declareVariable(14863, int.class, "self1", b.functionCall(14863, b.localVariable("self"), "intValue")), b.declareVariable(14864, int.class, "to1", b.functionCall(14864, b.localVariable("to"), "intValue")), b.declareVariable(14865, int.class, "stepNumber1", b.functionCall(14865, b.localVariable("stepNumber"), "intValue")), b.if_(b.logicalAnd(14866, b.greaterThan(14866, b.localVariable("stepNumber1"), b.constant(0)), b.greaterThan(14866, b.localVariable("to1"), b.localVariable("self1"))), b.block(b.forLoop(null, b.sequence(b.declareVariable(14867, int.class, "i", b.localVariable("self1"))), b.lessThan(14867, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.plusEqual(14867, b.localVariable("i"), b.localVariable("stepNumber1"))), b.block(b.functionCall(14868, b.localVariable("closure"), "call", b.localVariable("i"))))), b.if_(b.logicalAnd(14870, b.lessThan(14870, b.localVariable("stepNumber1"), b.constant(0)), b.lessThan(14870, b.localVariable("to1"), b.localVariable("self1"))), b.block(b.forLoop(null, b.sequence(b.declareVariable(14871, int.class, "i", b.localVariable("self1"))), b.greaterThan(14871, b.localVariable("i"), b.localVariable("to1")), b.sequence(b.plusEqual(14871, b.localVariable("i"), b.localVariable("stepNumber1"))), b.block(b.functionCall(14872, b.localVariable("closure"), "call", b.localVariable("i"))))), b.if_(b.compareNotEqual(14874, b.localVariable("self1"), b.localVariable("to1")), b.throw_(14875, b.new_(14875, GroovyRuntimeException.class, b.plus(14875, b.plus(14875, b.plus(14875, b.plus(14875, b.plus(14875, b.plus(14875, b.constant("Infinite loop in "), b.localVariable("self1")), b.constant(".step(")), b.localVariable("to1")), b.constant(", ")), b.localVariable("stepNumber1")), b.constant(")"))))))))))));
        throw new CpsCallableInvocation(f, null, self, to, stepNumber, closure);
    }

    public static void eachByte(Byte[] self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "eachByte", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "eachByte", self, closure))) {
            DefaultGroovyMethods.eachByte(self, closure);
            return ;
        }
        Builder b = new Builder(loc("eachByte"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.staticCall(15353, CpsDefaultGroovyMethods.class, "each", b.localVariable("self"), b.localVariable("closure"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static void eachByte(byte[] self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "eachByte", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "eachByte", self, closure))) {
            DefaultGroovyMethods.eachByte(self, closure);
            return ;
        }
        Builder b = new Builder(loc("eachByte"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.staticCall(15365, CpsDefaultGroovyMethods.class, "each", b.localVariable("self"), b.localVariable("closure"))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static int findIndexOf(Object self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "findIndexOf", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findIndexOf", self, closure))) {
            return DefaultGroovyMethods.findIndexOf(self, closure);
        }
        Builder b = new Builder(loc("findIndexOf"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(15378, CpsDefaultGroovyMethods.class, "findIndexOf", b.localVariable("self"), b.constant(0), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static int findIndexOf(Object self, int startIndex, Closure closure) {
        if ((!Caller.isAsynchronous(self, "findIndexOf", startIndex, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findIndexOf", self, startIndex, closure))) {
            return DefaultGroovyMethods.findIndexOf(self, startIndex, closure);
        }
        Builder b = new Builder(loc("findIndexOf"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "startIndex", "closure"), b.block(b.declareVariable(15393, int.class, "result", b.constant(-1)), b.declareVariable(15394, int.class, "i", b.constant(0)), b.declareVariable(15395, BooleanClosureWrapper.class, "bcw", b.new_(15395, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forLoop(null, b.sequence(b.declareVariable(15396, Iterator.class, "iter", b.functionCall(15396, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self")))), b.functionCall(15396, b.localVariable("iter"), "hasNext"), b.sequence(b.postfixInc(15396, b.localVariable("i"))), b.block(b.declareVariable(15397, Object.class, "value", b.functionCall(15397, b.localVariable("iter"), "next")), b.if_(b.lessThan(15398, b.localVariable("i"), b.localVariable("startIndex")), b.block(b.continue_(null))), b.if_(b.functionCall(15401, b.localVariable("bcw"), "call", b.localVariable("value")), b.block(b.assign(15402, b.localVariable("result"), b.localVariable("i")), b.break_(null))))), b.return_(b.localVariable("result"))));
        throw new CpsCallableInvocation(f, null, self, startIndex, closure);
    }

    public static int findLastIndexOf(Object self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "findLastIndexOf", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findLastIndexOf", self, closure))) {
            return DefaultGroovyMethods.findLastIndexOf(self, closure);
        }
        Builder b = new Builder(loc("findLastIndexOf"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(15419, CpsDefaultGroovyMethods.class, "findLastIndexOf", b.localVariable("self"), b.constant(0), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static int findLastIndexOf(Object self, int startIndex, Closure closure) {
        if ((!Caller.isAsynchronous(self, "findLastIndexOf", startIndex, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findLastIndexOf", self, startIndex, closure))) {
            return DefaultGroovyMethods.findLastIndexOf(self, startIndex, closure);
        }
        Builder b = new Builder(loc("findLastIndexOf"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "startIndex", "closure"), b.block(b.declareVariable(15434, int.class, "result", b.constant(-1)), b.declareVariable(15435, int.class, "i", b.constant(0)), b.declareVariable(15436, BooleanClosureWrapper.class, "bcw", b.new_(15436, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forLoop(null, b.sequence(b.declareVariable(15437, Iterator.class, "iter", b.functionCall(15437, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self")))), b.functionCall(15437, b.localVariable("iter"), "hasNext"), b.sequence(b.postfixInc(15437, b.localVariable("i"))), b.block(b.declareVariable(15438, Object.class, "value", b.functionCall(15438, b.localVariable("iter"), "next")), b.if_(b.lessThan(15439, b.localVariable("i"), b.localVariable("startIndex")), b.block(b.continue_(null))), b.if_(b.functionCall(15442, b.localVariable("bcw"), "call", b.localVariable("value")), b.block(b.assign(15443, b.localVariable("result"), b.localVariable("i")))))), b.return_(b.localVariable("result"))));
        throw new CpsCallableInvocation(f, null, self, startIndex, closure);
    }

    public static List<Number> findIndexValues(Object self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "findIndexValues", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findIndexValues", self, closure))) {
            return DefaultGroovyMethods.findIndexValues(self, closure);
        }
        Builder b = new Builder(loc("findIndexValues"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.return_(b.staticCall(15459, CpsDefaultGroovyMethods.class, "findIndexValues", b.localVariable("self"), b.constant(0), b.localVariable("closure")))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static List<Number> findIndexValues(Object self, Number startIndex, Closure closure) {
        if ((!Caller.isAsynchronous(self, "findIndexValues", startIndex, closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "findIndexValues", self, startIndex, closure))) {
            return DefaultGroovyMethods.findIndexValues(self, startIndex, closure);
        }
        Builder b = new Builder(loc("findIndexValues"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "startIndex", "closure"), b.block(b.declareVariable(15474, List.class, "result", b.new_(15474, ArrayList.class)), b.declareVariable(15475, long.class, "count", b.constant(0)), b.declareVariable(15476, long.class, "startCount", b.functionCall(15476, b.localVariable("startIndex"), "longValue")), b.declareVariable(15477, BooleanClosureWrapper.class, "bcw", b.new_(15477, BooleanClosureWrapper.class, b.localVariable("closure"))), b.forLoop(null, b.sequence(b.declareVariable(15478, Iterator.class, "iter", b.functionCall(15478, b.constant(InvokerHelper.class), "asIterator", b.localVariable("self")))), b.functionCall(15478, b.localVariable("iter"), "hasNext"), b.sequence(b.postfixInc(15478, b.localVariable("count"))), b.block(b.declareVariable(15479, Object.class, "value", b.functionCall(15479, b.localVariable("iter"), "next")), b.if_(b.lessThan(15480, b.localVariable("count"), b.localVariable("startCount")), b.block(b.continue_(null))), b.if_(b.functionCall(15483, b.localVariable("bcw"), "call", b.localVariable("value")), b.block(b.functionCall(15484, b.localVariable("result"), "add", b.localVariable("count")))))), b.return_(b.localVariable("result"))));
        throw new CpsCallableInvocation(f, null, self, startIndex, closure);
    }

    public static MetaClass metaClass(Class self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "metaClass", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "metaClass", self, closure))) {
            return DefaultGroovyMethods.metaClass(self, closure);
        }
        Builder b = new Builder(loc("metaClass"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(15769, MetaClassRegistry.class, "metaClassRegistry", b.functionCall(15769, b.constant(GroovySystem.class), "getMetaClassRegistry")), b.declareVariable(15770, MetaClass.class, "mc", b.functionCall(15770, b.localVariable("metaClassRegistry"), "getMetaClass", b.localVariable("self"))), b.if_(b.instanceOf(15772, b.localVariable("mc"), b.constant(ExpandoMetaClass.class)), b.block(b.functionCall(15773, b.cast(15773, b.localVariable("mc"), ExpandoMetaClass.class, false), "define", b.localVariable("closure")), b.return_(b.localVariable("mc"))), b.block(b.if_(b.logicalAnd(15777, b.instanceOf(15777, b.localVariable("mc"), b.constant(DelegatingMetaClass.class)), b.instanceOf(15777, b.functionCall(15777, b.cast(15777, b.localVariable("mc"), DelegatingMetaClass.class, false), "getAdaptee"), b.constant(ExpandoMetaClass.class))), b.block(b.functionCall(15778, b.cast(15778, b.functionCall(15778, b.cast(15778, b.localVariable("mc"), DelegatingMetaClass.class, false), "getAdaptee"), ExpandoMetaClass.class, false), "define", b.localVariable("closure")), b.return_(b.localVariable("mc"))), b.block(b.if_(b.logicalAnd(15782, b.instanceOf(15782, b.localVariable("mc"), b.constant(DelegatingMetaClass.class)), b.compareEqual(15782, b.functionCall(15782, b.functionCall(15782, b.cast(15782, b.localVariable("mc"), DelegatingMetaClass.class, false), "getAdaptee"), "getClass"), b.property(15782, b.constant(MetaClassImpl.class), "class"))), b.block(b.declareVariable(15783, ExpandoMetaClass.class, "emc", b.new_(15783, ExpandoMetaClass.class, b.localVariable("self"), b.constant(false), b.constant(true))), b.functionCall(15784, b.localVariable("emc"), "initialize"), b.functionCall(15785, b.localVariable("emc"), "define", b.localVariable("closure")), b.functionCall(15786, b.cast(15786, b.localVariable("mc"), DelegatingMetaClass.class, false), "setAdaptee", b.localVariable("emc")), b.return_(b.localVariable("mc"))), b.block(b.if_(b.compareEqual(15790, b.functionCall(15790, b.localVariable("mc"), "getClass"), b.property(15790, b.constant(MetaClassImpl.class), "class")), b.block(b.assign(15792, b.localVariable("mc"), b.new_(15792, ExpandoMetaClass.class, b.localVariable("self"), b.constant(false), b.constant(true))), b.functionCall(15793, b.localVariable("mc"), "initialize"), b.functionCall(15794, b.cast(15794, b.localVariable("mc"), ExpandoMetaClass.class, false), "define", b.localVariable("closure")), b.functionCall(15795, b.localVariable("metaClassRegistry"), "setMetaClass", b.localVariable("self"), b.localVariable("mc")), b.return_(b.localVariable("mc"))), b.block(b.throw_(15799, b.new_(15799, GroovyRuntimeException.class, b.plus(15799, b.constant("Can't add methods to custom meta class "), b.localVariable("mc"))))))))))))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    public static MetaClass metaClass(Object self, Closure closure) {
        if ((!Caller.isAsynchronous(self, "metaClass", closure))&&(!Caller.isAsynchronous(CpsDefaultGroovyMethods.class, "metaClass", self, closure))) {
            return DefaultGroovyMethods.metaClass(self, closure);
        }
        Builder b = new Builder(loc("metaClass"));
        CpsFunction f = new CpsFunction(Arrays.asList("self", "closure"), b.block(b.declareVariable(15816, MetaClass.class, "emc", b.staticCall(15816, CpsDefaultGroovyMethods.class, "hasPerInstanceMetaClass", b.localVariable("self"))), b.if_(b.compareEqual(15817, b.localVariable("emc"), b.constant(null)), b.block(b.declareVariable(15818, ExpandoMetaClass.class, "metaClass", b.new_(15818, ExpandoMetaClass.class, b.functionCall(15818, b.localVariable("self"), "getClass"), b.constant(false), b.constant(true))), b.functionCall(15819, b.localVariable("metaClass"), "initialize"), b.functionCall(15820, b.localVariable("metaClass"), "define", b.localVariable("closure")), b.if_(b.instanceOf(15821, b.localVariable("self"), b.constant(GroovyObject.class)), b.block(b.staticCall(15822, CpsDefaultGroovyMethods.class, "setMetaClass", b.cast(15822, b.localVariable("self"), GroovyObject.class, false), b.localVariable("metaClass"))), b.block(b.staticCall(15824, CpsDefaultGroovyMethods.class, "setMetaClass", b.localVariable("self"), b.localVariable("metaClass")))), b.return_(b.localVariable("metaClass"))), b.block(b.if_(b.instanceOf(15829, b.localVariable("emc"), b.constant(ExpandoMetaClass.class)), b.block(b.functionCall(15830, b.cast(15830, b.localVariable("emc"), ExpandoMetaClass.class, false), "define", b.localVariable("closure")), b.return_(b.localVariable("emc"))), b.block(b.if_(b.logicalAnd(15834, b.instanceOf(15834, b.localVariable("emc"), b.constant(DelegatingMetaClass.class)), b.instanceOf(15834, b.functionCall(15834, b.cast(15834, b.localVariable("emc"), DelegatingMetaClass.class, false), "getAdaptee"), b.constant(ExpandoMetaClass.class))), b.block(b.functionCall(15835, b.cast(15835, b.functionCall(15835, b.cast(15835, b.localVariable("emc"), DelegatingMetaClass.class, false), "getAdaptee"), ExpandoMetaClass.class, false), "define", b.localVariable("closure")), b.return_(b.localVariable("emc"))), b.block(b.throw_(15839, b.new_(15839, RuntimeException.class, b.plus(15839, b.constant("Can't add methods to non-ExpandoMetaClass "), b.localVariable("emc"))))))))))));
        throw new CpsCallableInvocation(f, null, self, closure);
    }

    private static MethodLocation loc(String methodName) {
        return new MethodLocation(CpsDefaultGroovyMethods.class, methodName);
    }

}
