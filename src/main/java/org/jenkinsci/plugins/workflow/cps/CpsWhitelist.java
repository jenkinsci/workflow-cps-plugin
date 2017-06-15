package org.jenkinsci.plugins.workflow.cps;

import com.cloudbees.groovy.cps.Continuable;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import hudson.model.Run;
import java.io.IOException;
import org.codehaus.groovy.runtime.GStringImpl;
import org.codehaus.groovy.runtime.ScriptBytecodeAdapter;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.AbstractWhitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.whitelists.ProxyWhitelist;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import jenkins.model.Jenkins;

/**
 * {@link Whitelist} implementation for CPS flow execution.
 *
 * @author Kohsuke Kawaguchi
 */
class CpsWhitelist extends AbstractWhitelist {

    private static final Logger LOGGER = Logger.getLogger(CpsWhitelist.class.getName());

    private CpsWhitelist() {}

    @Override
    public boolean permitsMethod(Method method, Object receiver, Object[] args) {
        if (receiver instanceof CpsScript) {
            String name = method.getName();
            if (name.equals("invokeMethod")) {
                // CpsScript dispatches to the DSL class
                return true;
            }
            if (name.equals("evaluate")) {
                // evaluate() family of methods are reimplemented in CpsScript for safe manner
                // but we can't allow arbitrary Script.evaluate() calls as that will escape sandbox
                return true;
            }
            if (name.equals("println") || name.equals("print") || name.equals("printf")) {
                // These are just aliases for EchoStep.
                return true;
            }
            if (name.equals("getProperty") && args.length == 1 && args[0] instanceof String) {
                String property = (String) args[0];
                CpsScript script = (CpsScript) receiver;
                Run<?,?> b = script.$buildNoException();
                if (GlobalVariable.byName(property, b) != null) {
                    return true;
                }
                if (b != null) {
                    try {
                        String value = EnvActionImpl.forRun(b).getProperty(property);
                        if (value != null) {
                            return true;
                        }
                    } catch (IOException x) {
                        LOGGER.log(Level.WARNING, null, x);
                    }
                }
            }
        }
        if (receiver instanceof DSL && method.getName().equals("invokeMethod")) {
            return true;
        }
        // TODO JENKINS-24982: it would be nice if AnnotatedWhitelist accepted @Whitelisted on an override
        if (receiver instanceof EnvActionImpl) {
            String name = method.getName();
            if (name.equals("getProperty") || name.equals("setProperty")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean permitsConstructor(Constructor<?> constructor, Object[] args) {
        if (constructor.getDeclaringClass()== GStringImpl.class)
            return true;

        return false;
    }

    @Override
    public boolean permitsStaticMethod(Method method, Object[] args) {
        Class<?> c = method.getDeclaringClass();
        String n = method.getName();
        // type coercive cast. In particular, this is used to build GString. See com.cloudbees.groovy.cps.Builder.gstring
        if (c == ScriptBytecodeAdapter.class && n.equals("asType")) {
            return true;
        }

        if (Continuable.categories.contains(c)) {
            // Delegate permission checks to the original *GroovyMethods.
            String cn = c.getName();
            String driverFrom = "com.cloudbees.groovy.cps.Cps"; // cf. Driver
            String driverTo = "org.codehaus.groovy.runtime.";
            if (cn.startsWith(driverFrom)) {
                try {
                    Class<?> orig = Class.forName(driverTo + cn.substring(driverFrom.length()));
                    Class<?>[] expectedParameterTypes = method.getParameterTypes();
                    String expectedName;
                    if (n.startsWith("$")) {
                        // E.g., CpsDefaultGroovyMethods.$each__java_util_List__groovy_lang_Closure
                        expectedName = n.substring(1).replaceFirst("__.+$", "");
                    } else {
                        expectedName = n;
                    }
                    for (Method m2 : orig.getMethods()) {
                        if (m2.getName().equals(expectedName) && Arrays.equals(m2.getParameterTypes(), expectedParameterTypes)) {
                            return Whitelist.all().permitsStaticMethod(m2, args);
                        }
                    }
                } catch (ClassNotFoundException x) {
                    LOGGER.log(Level.WARNING, null, x); // this would be unexpected
                }
            } else {
                LOGGER.log(Level.WARNING, "Unexpected category name {0}", cn); // as would this
            }
        }

        return false;
    }

    /**
     * Stuff we whitelist specifically for CPS, with the rest of the installed rules combined.
     */
    private static final Map<Jenkins,Whitelist> wrappedByJenkins = new WeakHashMap<Jenkins,Whitelist>();

    @SuppressFBWarnings(value="RCN_REDUNDANT_NULLCHECK_OF_NONNULL_VALUE", justification="TODO 1.653+ switch to Jenkins.getInstanceOrNull")
    static synchronized Whitelist get() {
        Jenkins j = Jenkins.getInstance();
        if (j == null) {
            return new ProxyWhitelist();
        }
        Whitelist wrapped = wrappedByJenkins.get(j);
        if (wrapped == null) {
            wrapped = new ProxyWhitelist(new CpsWhitelist(), Whitelist.all());
            wrappedByJenkins.put(j, wrapped);
        }
        return wrapped;
    }
}
