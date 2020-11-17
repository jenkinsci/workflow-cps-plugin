package org.jenkinsci.plugins.workflow.cps;

import org.jenkinsci.plugins.structs.describable.UninstantiatedDescribable;

import java.util.Map;
import java.util.Set;

public class UninstantiatedDescribableWithInterpolation extends UninstantiatedDescribable {
    private final Set<String> interpolatedStrings;

    public UninstantiatedDescribableWithInterpolation(String symbol, String klass, Map<String, ?> arguments, Set<String> interpolatedStrings) {
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
