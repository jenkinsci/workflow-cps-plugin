package org.jenkinsci.plugins.workflow.cps;

import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

/**
 * Some steps have complex argument types (e.g. `checkout` takes {@link hudson.scm.SCM}). When user use symbol-based
 * syntax with those arguments, an instance of this class is created as the result of {@link DSL#invokeDescribable(String, Object)}.
 *
 * <p>The difference between this class and its parent, {@link UninstantiatedDescribable}, is that this class stores the Groovy interpolated strings
 * that were encountered in {@link DSL#flattenGString(Object, Set)} via {@link DSL.NamedArgsAndClosure}</p>
 */
@Restricted(NoExternalUse.class)
public class UninstantiatedDescribableWithInterpolation extends UninstantiatedDescribable {
    private static final long serialVersionUID = 1L;
    private final Set<String> interpolatedStrings;

    public UninstantiatedDescribableWithInterpolation(
            String symbol, String klass, Map<String, ?> arguments, Set<String> interpolatedStrings) {
        super(symbol, klass, arguments);
        this.interpolatedStrings = interpolatedStrings;
    }

    public Set<String> getInterpolatedStrings() {
        return interpolatedStrings;
    }

    @Override
    public boolean equals(Object o) {
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }
}
