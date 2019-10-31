package org.jenkinsci.plugins.workflow;

import hudson.Extension;
import hudson.model.Item;
import org.jenkinsci.plugins.workflow.flow.DurabilityHintProvider;
import org.jenkinsci.plugins.workflow.flow.FlowDurabilityHint;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

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

    public void registerHint(@Nonnull Item x, @Nonnull FlowDurabilityHint myHint) {
        registerHint(x.getFullName(), myHint);
    }

    public void registerHint(@Nonnull String itemfullName, @Nonnull FlowDurabilityHint myHint) {
        hintMapping.put(itemfullName, myHint);
    }

    public boolean removeHint(@Nonnull Item x) {
        return removeHint(x.getFullName());
    }

    public boolean removeHint(@Nonnull String itemFullName) {
        return hintMapping.remove(itemFullName) != null;
    }

    public Map<String, FlowDurabilityHint> getMappings() {
        return new HashMap<>(this.hintMapping);
    }

    @CheckForNull
    @Override
    public FlowDurabilityHint suggestFor(@Nonnull Item x) {
        return hintMapping.get(x.getFullName());
    }
}
