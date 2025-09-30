package org.jenkinsci.plugins.workflow;

import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;
import hudson.model.Item;
import java.util.HashMap;
import java.util.Map;
import org.jenkinsci.plugins.workflow.flow.DurabilityHintProvider;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;

/**
 * Durability hint provider that can be custom-configured on a per-job basis without requiring downstream logic.
 */
@Extension
public class TestDurabilityHintProvider implements DurabilityHintProvider {
    private HashMap<String, FlowDurabilityHint> hintMapping = new HashMap<>();

    @Override
    public int ordinal() {
        return -1;
    }

    public void registerHint(@NonNull Item x, @NonNull FlowDurabilityHint myHint) {
        registerHint(x.getFullName(), myHint);
    }

    public void registerHint(@NonNull String itemfullName, @NonNull FlowDurabilityHint myHint) {
        hintMapping.put(itemfullName, myHint);
    }

    public boolean removeHint(@NonNull Item x) {
        return removeHint(x.getFullName());
    }

    public boolean removeHint(@NonNull String itemFullName) {
        return hintMapping.remove(itemFullName) != null;
    }

    public Map<String, FlowDurabilityHint> getMappings() {
        return new HashMap<>(this.hintMapping);
    }

    @CheckForNull
    @Override
    public FlowDurabilityHint suggestFor(@NonNull Item x) {
        return hintMapping.get(x.getFullName());
    }
}
