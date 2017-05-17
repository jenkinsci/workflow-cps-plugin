package com.cloudbees.groovy.cps.tool;

import com.google.common.collect.ImmutableSet;
import com.sun.codemodel.writer.FileCodeWriter;
import com.sun.tools.javac.api.JavacTool;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.file.RelativePath.RelativeDirectory;
import com.sun.tools.javac.file.ZipArchive;
import groovy.lang.Closure;
import groovy.lang.GroovyShell;
import hudson.remoting.Which;

import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.DeclaredType;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.File;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Predicate;
import java.util.zip.ZipFile;

import static java.util.Arrays.*;

/**
 * @author Kohsuke Kawaguchi
 */
public class Driver {
    public static void main(String[] args) throws Exception {
        new Driver().run(new File(args[0]));
    }

    public void run(File dir) throws Exception {
        JavaCompiler javac = JavacTool.create();
        DiagnosticListener<JavaFileObject> errorListener = createErrorListener();

        try (StandardJavaFileManager fileManager = javac.getStandardFileManager(errorListener, Locale.getDefault(), Charset.defaultCharset())) {
            fileManager.setLocation(StandardLocation.CLASS_PATH,
                    Collections.singleton(Which.jarFile(GroovyShell.class)));

            File groovySrcJar = Which.jarFile(Driver.class.getClassLoader().getResource("groovy/lang/GroovyShell.java"));

            // classes to translate
            // TODO include other classes mentioned in DgmConverter just in case (first see TODO in Translator.translate); and certainly StringGroovyMethods which has some Closure-based methods
            List<String> fileNames = asList("DefaultGroovyMethods", "ProcessGroovyMethods", "DefaultGroovyStaticMethods");

            List<JavaFileObject> src = new ArrayList<>();
            ZipArchive a = new ZipArchive((JavacFileManager) fileManager, new ZipFile(groovySrcJar));

            for (String name : fileNames) {
                src.add(a.getFileObject(new RelativeDirectory("org/codehaus/groovy/runtime"),name+".java"));
            }

            // annotation processing appears to cause the source files to be reparsed
            // (even though I couldn't find exactly where it's done), which causes
            // Tree symbols created by the original JavacTask.parse() call to be thrown away,
            // which breaks later processing.
            // So for now, don't perform annotation processing
            List<String> options = asList("-proc:none");

            Translator t = new Translator(javac.getTask(null, fileManager, errorListener, options, null, src));

            final DeclaredType closureType = t.types.getDeclaredType(t.elements.getTypeElement(Closure.class.getName()));

            // TODO move all this into Translator
            Predicate<ExecutableElement> selector = (e) -> {
                boolean r =
                        // Only interested here in methods; not currently handling nested classes.
                        e.getKind() == ElementKind.METHOD
                        // Top-level invocations can only be to public static methods. But some private/protected static helper methods need translation, too.
                        && e.getModifiers().contains(Modifier.STATIC)
                        // Has a Closure parameter in one of the arguments (not in the receiver).
                        // TODO need to accept nonpublic (helper) methods taking Closure as the first parameter. In fact may need to accept any helper methods.
                        && e.getParameters().subList(1, e.getParameters().size()).stream()
                                .anyMatch(p -> t.types.isAssignable(p.asType(), closureType))
                        // Ran into problems resolving overloads from these methods. TODO might be obsolete with new overload delegation system.
                        && e.getAnnotation(Deprecated.class) == null;
                //System.err.println("Translating " + e + "? " + r);
                return r;
            };

            for (String name : fileNames) {
                t.translate(
                        "org.codehaus.groovy.runtime."+name,
                        "com.cloudbees.groovy.cps.Cps"+name,
                        selector,
                        e -> !EXCLUSIONS.contains(e.getEnclosingElement().getSimpleName().toString() + "." + e.getSimpleName().toString()),
                        groovySrcJar.getName());
            }


            dir.mkdirs();
            t.generateTo(new FileCodeWriter(dir));
        }
    }

    private DiagnosticListener<JavaFileObject> createErrorListener() {
        return System.out::println;
    }

    private static final Set<String> EXCLUSIONS = ImmutableSet.of(
            "DefaultGroovyMethods.runAfter", /* use anonymous inner class we can't handle */
            "DefaultGroovyMethods.accept" /* launches a thread */,
            "DefaultGroovyStaticMethods.start", "DefaultGroovyStaticMethods.startDaemon", // ditto
            "DefaultGroovyMethods.filterLine",    /* anonymous inner classes */
            "DefaultGroovyMethods.dropWhile","DefaultGroovyMethods.takeWhile" /* TODO: translate inner classes to support this*/,
            "DefaultGroovyMethods.toUnique", // ditto: UniqueIterator is private

            "ProcessGroovyMethods.withWriter",
            "ProcessGroovyMethods.withOutputStream" /* anonymous inner class */
    );
}
