package com.cloudbees.groovy.cps;

import com.cloudbees.groovy.cps.impl.CpsClosure;
import groovy.lang.Closure;
import java.util.ArrayList;
import java.util.List;
import org.codehaus.groovy.ast.ClassNode;

/**
 * Switches that affect the behaviour of {@link CpsTransformer}
 *
 * @author Kohsuke Kawaguchi
 * @see CpsTransformer#setConfiguration(TransformerConfiguration)
 */
public class TransformerConfiguration {
    private ClassNode closureType = new ClassNode(CpsClosure.class);
    private List<Safepoint> safepoints = new ArrayList<>();

    public ClassNode getClosureType() {
        return closureType;
    }

    /*package*/ List<Safepoint> getSafepoints() {
        return safepoints;
    }

    public TransformerConfiguration withClosureType(ClassNode closureType) {
        this.closureType = closureType;
        return this;
    }

    public TransformerConfiguration withClosureType(Class<? extends Closure> c) {
        return withClosureType(new ClassNode(c));
    }

    /**
     * Inserts a safepoint into transformed program.
     *
     * <p>
     * At every loop head and at the entry of a function, CPS-transformed program
     * will call the specified no-arg static public method that returns void.
     * This is useful to pause the execution of un-cooperative CPS transformed programs.
     *
     * <p>
     * A safepoint method can run some computation and return normally to keep the
     * CPS interpreter going, or it can {@linkplain Continuable#suspend(Object) suspend}
     * the execution of a program.
     */
    public TransformerConfiguration withSafepoint(Class clazz, String methodName) {
        safepoints.add(new Safepoint(clazz, methodName));
        return this;
    }
}
