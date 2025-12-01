package com.cloudbees.groovy.cps.impl;

import com.cloudbees.groovy.cps.Block;
import com.cloudbees.groovy.cps.Continuation;
import com.cloudbees.groovy.cps.CpsTransformer;
import com.cloudbees.groovy.cps.Env;
import com.cloudbees.groovy.cps.Next;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.codehaus.groovy.ast.expr.SpreadExpression;
import org.codehaus.groovy.ast.expr.SpreadMapExpression;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.codehaus.groovy.runtime.typehandling.DefaultTypeTransformation;

/**
 * Handles {@link SpreadExpression} similarly to the way that {@link SpreadMapBlock} handles {@link SpreadMapExpression},
 * but with a {@code groovy-cps}-specific {@link SpreadList} marker class.
 *
 * <p>We use a marker class because we cannot easily mimic the way that Groovy normally handles {@link SpreadExpression}.
 * To do so, we would need modifications to {@link CpsTransformer} for list and argument list visitors akin to {@code AsmClassGenerator.despreadList},
 * a new implementation of {@link Block} that would fix those expressions and then call {@link ScriptBytecodeAdapter#despreadList},
 * and a variant of {@link FunctionCallBlock} that takes a single {@link Block} which is expected to evaluate to {@code Object[]}
 * (rather than a {@code Block[]}) so that the result of {@code despreadList} can be used directly as the arguments array
 * for the function call.
 */
public class SpreadBlock implements Block {
    private final SourceLocation loc;
    private final Block listExp;

    public SpreadBlock(SourceLocation loc, Block listExp) {
        this.loc = loc;
        this.listExp = listExp;
    }

    @Override
    public Next eval(Env e, Continuation k) {
        return new ContinuationImpl(e, k).then(listExp, e, fixList);
    }

    class ContinuationImpl extends ContinuationGroup {
        private final Env e;
        private final Continuation k;

        ContinuationImpl(Env e, Continuation k) {
            this.e = e;
            this.k = k;
        }

        public Next fixList(Object value) {
            try {
                return k.receive(new SpreadList(despreadList(value).toArray()));
            } catch (IllegalArgumentException t) {
                return throwException(e, t, loc, new ReferenceStackTrace());
            }
        }

        // c.f.
        // https://github.com/apache/groovy/blob/bd12deac1d73b036d6bae378b69cfdb2cf692490/src/main/java/org/codehaus/groovy/runtime/ScriptBytecodeAdapter.java#L908-L920
        private List<Object> despreadList(Object value) {
            if (value == null) {
                return Collections.singletonList(null);
            } else if (value instanceof List) {
                return (List<Object>) value;
            } else if (value.getClass().isArray()) {
                return DefaultTypeTransformation.primitiveArrayToList(value);
            } else {
                String error = "cannot spread the type " + value.getClass().getName() + " with value " + value;
                if (value instanceof Map) {
                    error += ", did you mean to use the spread-map operator instead?";
                }
                throw new IllegalArgumentException(error);
            }
        }

        private static final long serialVersionUID = 1L;
    }

    /**
     * Holds the expanded value until it is interpolated into its surrounding context by {@link ListBlock} or {@link FunctionCallBlock}.
     */
    static class SpreadList implements Serializable {
        private final Object[] expanded;

        public SpreadList(Object[] expanded) {
            this.expanded = expanded;
        }

        private static final long serialVersionUID = 1L;
    }

    public static Object[] despreadList(Object[] list) {
        List<Object> expanded = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof SpreadList) {
                Collections.addAll(expanded, ((SpreadList) element).expanded);
            } else {
                expanded.add(element);
            }
        }
        return expanded.toArray();
    }

    static final ContinuationPtr fixList = new ContinuationPtr(ContinuationImpl.class, "fixList");

    private static final long serialVersionUID = 1L;
}
