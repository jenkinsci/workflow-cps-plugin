package com.cloudbees.groovy.cps.tool;

import com.sun.codemodel.writer.FileCodeWriter;
import groovy.lang.GroovyShell;
import hudson.remoting.Which;
import java.io.File;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.tools.DiagnosticListener;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;

public class Driver {
    public static void main(String[] args) throws Exception {
        new Driver().run(new File(args[0]));
    }

    public void run(File dir) throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticListener<JavaFileObject> errorListener = createErrorListener();

        try (StandardJavaFileManager fileManager =
                javac.getStandardFileManager(errorListener, Locale.getDefault(), Charset.defaultCharset())) {
            fileManager.setLocation(StandardLocation.CLASS_PATH, Set.of(Which.jarFile(GroovyShell.class)));

            File groovySrcJar =
                    Which.jarFile(Driver.class.getClassLoader().getResource("groovy/lang/GroovyShell.java"));

            // classes to translate
            // TODO include other classes mentioned in DefaultGroovyMethods.DGM_LIKE_CLASSES if they have any applicable
            // methods
            List<String> fileNames =
                    List.of("DefaultGroovyMethods", "DefaultGroovyStaticMethods", "StringGroovyMethods");

            List<JavaFileObject> src = new ArrayList<>();
            for (JavaFileObject jfo : fileManager.list(
                    StandardLocation.CLASS_PATH,
                    "org.codehaus.groovy.runtime",
                    Set.of(JavaFileObject.Kind.SOURCE),
                    true)) {
                for (String name : fileNames) {
                    if (jfo.toUri().toString().endsWith("/org/codehaus/groovy/runtime/" + name + ".java")) {
                        src.add(jfo);
                        break;
                    }
                }
            }

            // annotation processing appears to cause the source files to be reparsed
            // (even though I couldn't find exactly where it's done), which causes
            // Tree symbols created by the original JavacTask.parse() call to be thrown away,
            // which breaks later processing.
            // So for now, don't perform annotation processing
            List<String> options = List.of("-proc:none");

            Translator t = new Translator(javac.getTask(null, fileManager, errorListener, options, null, src));

            for (String name : fileNames) {
                t.translate(
                        "org.codehaus.groovy.runtime." + name,
                        "com.cloudbees.groovy.cps.Cps" + name,
                        groovySrcJar.getName());
            }

            Files.createDirectories(dir.toPath());
            t.generateTo(new FileCodeWriter(dir));
        }
    }

    private DiagnosticListener<JavaFileObject> createErrorListener() {
        return System.out::println;
    }
}
