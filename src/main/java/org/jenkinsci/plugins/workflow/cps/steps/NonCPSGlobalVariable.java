package org.jenkinsci.plugins.workflow.cps.steps;

import groovy.lang.Binding;
import hudson.Extension;
import org.jenkinsci.plugins.workflow.cps.CpsScript;
import org.jenkinsci.plugins.workflow.cps.GlobalVariable;
import org.kohsuke.accmod.Restricted;
import org.kohsuke.accmod.restrictions.NoExternalUse;

import javax.annotation.Nonnull;

/**
 * A wrapper, which calls steps inside within NonCPS instead of standard interpreter.
 * It allows saving time and reducing code complexity, because one does not need to create new methods for it.
 * @author Oleg Nenashev
 * @since TODO
 */
@Restricted(NoExternalUse.class)
@Extension
public class NonCPSGlobalVariable extends GlobalVariable {

    @Nonnull
    @Override
    public String getName() {
        return "nonCPS";
    }

    @Override
    public final Object getValue(CpsScript script) throws Exception {
        final Binding binding = script.getBinding();
        final Object loadedObject;
        if (binding.hasVariable(getName())) {
            loadedObject = binding.getVariable(getName());
        } else {
            // Note that if this were a method rather than a constructor, we would need to mark it @NonCPS lest it throw CpsCallableInvocation.
            loadedObject = script.getClass().getClassLoader().loadClass(NonCPSGlobalVariable.class.getName() + ".NonCPSWrapperImpl").getConstructor().newInstance();
            binding.setVariable(getName(), loadedObject);
        }
        return loadedObject;
    }


}
