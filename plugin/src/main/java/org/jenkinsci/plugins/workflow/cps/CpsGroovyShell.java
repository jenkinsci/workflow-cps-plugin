package org.jenkinsci.plugins.workflow.cps;

import groovy.lang.Binding;
import groovy.lang.GroovyClassLoader;
import groovy.lang.GroovyCodeSource;
import groovy.lang.GroovyShell;
import groovy.lang.Script;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.Enumeration;
import java.util.logging.Level;
import java.util.logging.Logger;
import edu.umd.cs.findbugs.annotations.CheckForNull;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.File;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.codehaus.groovy.control.CompilationFailedException;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.CompilerConfiguration;
import org.codehaus.groovy.control.SourceUnit;
import org.jenkinsci.plugins.scriptsecurity.sandbox.Whitelist;
import org.jenkinsci.plugins.scriptsecurity.sandbox.groovy.GroovySandbox;

/**
 * {@link GroovyShell} with additional tweaks necessary to run {@link CpsScript}
 * @see "doc/classloader.md"
 * @see CpsGroovyShellFactory
 */
class CpsGroovyShell extends GroovyShell {

    private static final Logger LOGGER = Logger.getLogger(CpsGroovyShell.class.getName());

    /**
     * {@link CpsFlowExecution} for which this shell is created.
     *
     * Null is a valid value for just parsing the script to test-compile it without running it.
     */
    private final @CheckForNull CpsFlowExecution execution;

    /**
     * Use {@link CpsGroovyShellFactory} to instantiate it.
     */
    CpsGroovyShell(ClassLoader parent, @CheckForNull CpsFlowExecution execution, CompilerConfiguration cc) {
        this(execution, cc, execution != null ? new TimingLoader(parent, execution) : parent);
    }

    private CpsGroovyShell(@CheckForNull CpsFlowExecution execution, CompilerConfiguration cc, ClassLoader usuallyTimingLoader) {
        super(usuallyTimingLoader, new Binding(), cc);
        this.execution = execution;
        try {
            // Apparently there is no supported way to initialize a GroovyShell with a specified GroovyClassLoader.
            Field loaderF = GroovyShell.class.getDeclaredField("loader");
            loaderF.setAccessible(true);
            loaderF.set(this, new CleanGroovyClassLoader(usuallyTimingLoader, cc));
        } catch (Exception x) {
            LOGGER.log(Level.WARNING, "failed to install CleanGroovyClassLoader", x);
        }
    }

    /**
     * Disables the weird and unreliable {@link groovy.lang.GroovyClassLoader.InnerLoader}.
     * This is apparently only necessary when you are using class recompilation, which we are not.
     * We want the {@linkplain Class#getClassLoader defining loader} of {@code *.groovy} to be this one.
     * Otherwise the defining loader will be an {@code InnerLoader}, and not necessarily the same instance from load to load.
     * @see GroovyClassLoader#getTimeStamp
     */
    private static final class CleanGroovyClassLoader extends GroovyClassLoader {

        CleanGroovyClassLoader(ClassLoader loader, CompilerConfiguration config) {
            super(loader, config);
        }

        @Override protected ClassCollector createCollector(CompilationUnit unit, SourceUnit su) {
            // Super implementation is what creates the InnerLoader.
            return new CleanClassCollector(unit, su);
        }

        private static final Pattern JAR_URL = Pattern.compile("jar:(file:/.+[.]jar)!/.+");

        // Avoid expensive and (JDK-6956385) leaky implementations of certain JarURLConnection methods:
        @Override public URL findResource(String name) {
            URL url = super.findResource(name);
            if (url != null && url.getProtocol().equals("jar")) {
                try {
                    return new URL(url.getProtocol(), url.getHost(), url.getPort(), url.getFile(), new URLStreamHandler() {
                        @Override protected URLConnection openConnection(URL url2) throws IOException {
                            URLConnection delegate = url.openConnection();
                            return new URLConnection(url2) {
                                @Override public void connect() throws IOException {
                                    delegate.connect();
                                }
                                @Override public InputStream getInputStream() throws IOException {
                                    return delegate.getInputStream();
                                }
                                @Override public String getHeaderField(String name) {
                                    return delegate.getHeaderField(name);
                                }
                                @Override public long getLastModified() {
                                    Matcher m = JAR_URL.matcher(url.toString());
                                    if (m.matches()) {
                                        return new File(URI.create(m.group(1))).lastModified();
                                    }
                                    return delegate.getLastModified();
                                }
                                @Override public String getContentEncoding() {
                                    return null;
                                }
                            };
                        }
                    });
                } catch (MalformedURLException x) {
                    LOGGER.log(Level.WARNING, null, x);
                }
            }
            return url;
        }

        private final class CleanClassCollector extends ClassCollector {

            CleanClassCollector(CompilationUnit unit, SourceUnit su) {
                // Cannot override {@code final cl} field so have to do it this way.
                super(null, unit, su);
            }

            @Override public GroovyClassLoader getDefiningClassLoader() {
                return CleanGroovyClassLoader.this;
            }

        }

    }

    public void prepareScript(Script script) {
        if (script instanceof CpsScript) {
            CpsScript cs = (CpsScript) script;
            cs.execution = execution;
            try {
                cs.$initialize();
            } catch (IOException e) {
                // TODO: write a library to let me throw this
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * When loading additional scripts, we need to remember its source text so that
     * when we come back from persistence we can load them again.
     */
    @Override
    public Script parse(GroovyCodeSource codeSource) throws CompilationFailedException {
        Script s = doParse(codeSource);
        if (execution!=null) {
            execution.loadedScripts.put(s.getClass().getSimpleName(), codeSource.getScriptText());
            execution.saveExecutionIfDurable();
        }
        prepareScript(s);
        return s;
    }

    /**
     * Used internally to reload the script back when coming back from the persisted state
     * (therefore we don't want to record this.)
     */
    /*package*/ Script reparse(String className, String text) throws CompilationFailedException {
        return doParse(new GroovyCodeSource(text,className,DEFAULT_CODE_BASE));
    }

    private Script doParse(GroovyCodeSource codeSource) throws CompilationFailedException {
        GroovySandbox sandbox = new GroovySandbox();
        if (execution != null) {
            sandbox.withWhitelist(new GroovyClassLoaderWhitelist(Whitelist.all(),
                execution.getTrustedShell().getClassLoader(),
                execution.getShell().getClassLoader()));
        } else {
            sandbox.withWhitelist(new GroovyClassLoaderWhitelist(Whitelist.all(), getClassLoader()));
        }
        try (GroovySandbox.Scope scope = sandbox.enter()) {
            if (execution != null) {
                try (CpsFlowExecution.Timing t = execution.time(CpsFlowExecution.TimingKind.parse)) {
                    return scope.parse(CpsGroovyShell.this, codeSource);
                }
            } else {
                return scope.parse(CpsGroovyShell.this, codeSource);
            }
        }
    }

    /**
     * Every script we parse get caught into {@code execution.loadedScripts}, so the size
     * yields a unique enough ID.
     */
    @Override
    protected synchronized String generateScriptName() {
        if (execution!=null)
            return "Script" + (execution.loadedScripts.size()+1) + ".groovy";
        else
            return super.generateScriptName();
    }

    static class TimingLoader extends ClassLoader {
        private final @NonNull CpsFlowExecution execution;
        TimingLoader(ClassLoader parent, @NonNull CpsFlowExecution execution) {
            super(parent);
            this.execution = execution;
        }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            try (CpsFlowExecution.Timing t = execution.time(CpsFlowExecution.TimingKind.classLoad)) {
                return super.loadClass(name, resolve);
            }
        }
        @Override public URL getResource(String name) {
            try (CpsFlowExecution.Timing t = execution.time(CpsFlowExecution.TimingKind.classLoad)) {
                return super.getResource(name);
            }
        }
        @Override public Enumeration<URL> getResources(String name) throws IOException {
            try (CpsFlowExecution.Timing t = execution.time(CpsFlowExecution.TimingKind.classLoad)) {
                return super.getResources(name);
            }
        }
    }

}
