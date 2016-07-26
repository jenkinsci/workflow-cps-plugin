package org.jenkinsci.plugins.workflow.cps.steps.ingroovy;

import groovy.lang.Closure;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.workflow.cps.CpsVmThreadOnly;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.kohsuke.stapler.ClassDescriptor;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

/**
 * A {@link Step} written entirely in Groovy.
 *
 * Unlike regular extensions, there will be one instance of {@link StepDescriptorInGroovy}
 * for every step written in Groovy.
 *
 * @author Kohsuke Kawaguchi
 */
public class StepInGroovy extends Step {
    private final Map<String, Object> arguments;

    // not data bindable
    public StepInGroovy(Map<String, Object> arguments) {
        this.arguments = arguments;
    }

    @Override
    @CpsVmThreadOnly("CPS program calls this, which is run by CpsVmThread")
    public StepExecution start(StepContext context) throws Exception {
        return new StepInGroovyExecution(getDescriptor(), arguments, context);
    }

    @Override
    public StepDescriptorInGroovy getDescriptor() {
        return (StepDescriptorInGroovy)super.getDescriptor();
    }

    // not @Extension by itself
    public static class StepDescriptorInGroovy extends StepDescriptor {
        private final String functionName;

        /**
         * Groovy source file compiled with normal Groovy compiler, not CPS.
         * Useful for introspection.
         */
        private final StepInGroovyScript compiled;
        /**
         * The 'call' method on {@link #compiled}
         */
        private final Method callMethod;
        private final List<Parameter> params = new ArrayList<>();

        public StepDescriptorInGroovy(String functionName) throws IOException {
            this.functionName = functionName;
            this.compiled = GroovyCompiler.get().parse(this);
            this.callMethod = findCallMethod();

            Class<?>[] p = callMethod.getParameterTypes();
            String[] n = ClassDescriptor.loadParameterNames(callMethod);
            assert p.length==n.length;
            for (int i=0; i<n.length; i++) {
                params.add(new Parameter(p[i],n[i]));
            }
        }

        private Method findCallMethod() {
            for (Method m : compiled.getClass().getMethods()) {
                if (m.getName().equals("call"))  {
                    return m;
                }
            }
            throw new IllegalArgumentException(getSourceFile()+" does not have a public call method");
        }

        /**
         * Parameters defined on this step, which are those that are defined on the 'call' method.
         */
        public List<Parameter> getParameters() {
            return params;
        }

        /**
         * Where is the Groovy source code of this step?
         */
        public URL getSourceFile() {
            return Jenkins.getInstance().pluginManager.uberClassLoader.getResource("steps/"+functionName+".groovy");
        }

        /**
         * {@link #clazz} doesn't uniquely identify a descriptor,
         * so we need more things to make it unique.
         */
        @Override
        public String getId() {
            return super.getId()+":"+functionName;
        }

        /**
         * If the groovy code takes the closure parameter in the end, that's a sign that we take
         * body.
         */
        @Override
        public boolean takesImplicitBlockArgument() {
            return params.isEmpty() && Closure.class.isAssignableFrom(params.get(params.size()-1).type);
        }

        @Override
        public Set<Class<?>> getRequiredContext() {
            return Collections.emptySet();
        }

        @Override
        public String getFunctionName() {
            return functionName;
        }

        @Nonnull
        @Override
        public String getDisplayName() {
            return compiled.getDisplayName();
        }

        @Override
        public Step newInstance(Map<String, Object> arguments) throws Exception {
            return new StepInGroovy(arguments);
        }
    }
}
