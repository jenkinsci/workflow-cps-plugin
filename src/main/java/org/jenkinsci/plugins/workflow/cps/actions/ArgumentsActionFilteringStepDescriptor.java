package org.jenkinsci.plugins.workflow.cps.actions;

import org.jenkinsci.plugins.workflow.steps.Step;

import javax.annotation.Nonnull;
import java.util.Map;

/**
 * Interface for {@link org.jenkinsci.plugins.workflow.steps.Step}, which allows filtering actions to be stored.
 * @author Oleg Nenashev
 * @since TODO
 */
public interface ArgumentsActionFilteringStepDescriptor {

    @Nonnull
    Map<String, Object> filterForAction(@Nonnull Step step, @Nonnull Map<String, Object> arguments);
}
