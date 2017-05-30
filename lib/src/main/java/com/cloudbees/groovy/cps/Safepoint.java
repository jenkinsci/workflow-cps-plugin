package com.cloudbees.groovy.cps;

import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.stmt.Statement;

/**
 * Safepoint method.
 *
 * <p>
 * Represents a static method with no-argument that invoked
 * at every loop head and function call entry to enable stop-the-world pause,
 * even in the face of non-cooperating CPS programs.
 *
 * <p>
 * This is analogous to the HotSpot JVM safepoints.
 *
 * @author Kohsuke Kawaguchi
 * @see TransformerConfiguration#withSafepoint(Class, String)
 * @see CpsTransformer#visitWithSafepoint(Statement)
 */
/*package*/ final class Safepoint {
    final Class clazz;
    final ClassNode node;
    final String methodName;

    Safepoint(Class clazz, String methodName) {
        this.clazz = clazz;
        this.node = ClassHelper.makeCached(clazz);
        this.methodName = methodName;
    }
}
